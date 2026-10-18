package com.saasno.service;

import static com.saasno.service.AnalyticsSupport.lng;
import static com.saasno.service.AnalyticsSupport.str;
import static com.saasno.service.SqlHelper.clean;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.entity.BranchEntity;
import com.saasno.repository.BranchRepository;
import com.saasno.utility.BranchScope;
import com.saasno.utility.dto.purchase.SupplierDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.generator.ID;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataResult;

// Supplier. Milik satu cabang: terlihat oleh cabang itu, turunan, dan induknya (seperti produk), tetapi hanya diubah oleh
// cabang pemilik dan induknya (cakupan). Supplier yang pernah dipakai pesanan tidak dihapus, hanya dinonaktifkan.
@Service
public class SupplierService {

	private static final int MAX_PAGE = 200;
	private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyMM");

	@Autowired
	private SqlHelper sql;

	@Autowired
	private BranchScopeService branchScopeService;

	@Autowired
	private BranchRepository branchRepository;

	@Transactional(readOnly = true)
	public ResponseEntity<DataResult<SupplierDTO>> read(String name, String status, String branchId, Integer offset, Integer limit) {
		BranchScope scope = branchScopeService.current();
		StringBuilder where = new StringBuilder("1 = 1");
		Map<String, Object> params = new HashMap<>();
		if (!scope.isGlobal()) {
			where.append(" AND s.branch_id IN (:visible)");
			params.put("visible", scope.getVisibleIds());
		}
		String wantedBranch = clean(branchId);
		if (wantedBranch != null) {
			if (!scope.canSee(wantedBranch)) {
				throw new ApiException("404", "Cabang tidak ditemukan");
			}
			where.append(" AND s.branch_id = :wanted");
			params.put("wanted", wantedBranch);
		}
		String wantedName = clean(name);
		if (wantedName != null) {
			where.append(" AND (LOWER(s.name) LIKE :name OR LOWER(s.serial) LIKE :name)");
			params.put("name", "%" + wantedName.toLowerCase() + "%");
		}
		String wantedStatus = clean(status);
		if (wantedStatus != null) {
			where.append(" AND s.status = :status");
			params.put("status", wantedStatus);
		}
		long total = lng(sql.rows("SELECT COUNT(*) FROM public.supplier s WHERE " + where, params).get(0)[0]);
		int size = limit == null || limit < 1 ? 10 : Math.min(limit, MAX_PAGE);
		int start = offset == null || offset < 0 ? 0 : offset;
		List<Object[]> rows = sql.rows("SELECT s.id, s.serial, s.branch_id, b.name, s.name, s.contact_name, s.phone, s.address, s.note, s.status "
				+ "FROM public.supplier s JOIN public.branch b ON b.id = s.branch_id WHERE " + where + " ORDER BY s.name, s.serial LIMIT " + size
				+ " OFFSET " + start, params);
		List<SupplierDTO> list = new ArrayList<>();
		for (Object[] r : rows) {
			list.add(dto(r));
		}
		return ResponseEntity.ok(new DataResult<>(list, BigInteger.valueOf(total), BigInteger.valueOf(total)));
	}

	@Transactional
	public ResponseModel<SupplierDTO> create(SupplierDTO request) {
		BranchScope scope = branchScopeService.current();
		String owner = branchScopeService.resolveOwner(scope, request.branchId());
		Fields f = validate(request);
		BranchEntity branch = branchRepository.find(owner);
		String prefix = "SUP/" + branch.getSerial() + "/" + LocalDateTime.now().format(YEAR) + "/";
		sql.lock("supplier_serial:" + prefix);
		List<Object[]> latest = sql.rows("SELECT MAX(s.serial) FROM public.supplier s WHERE s.serial LIKE :prefix", "prefix", prefix + "%");
		int sequence = latest.isEmpty() || latest.get(0)[0] == null ? 1 : Integer.parseInt(str(latest.get(0)[0]).substring(prefix.length())) + 1;
		String id = ID.uuid();
		LocalDateTime now = LocalDateTime.now();
		sql.execute("INSERT INTO public.supplier (id, serial, branch_id, name, contact_name, phone, address, note, status, registered, modificated) "
				+ "VALUES (:id, :serial, :branch, :name, :contact, :phone, :address, :note, :status, :now, :now)",
				"id", id, "serial", prefix + String.format("%04d", sequence), "branch", owner, "name", f.name(), "contact", f.contact(),
				"phone", f.phone(), "address", f.address(), "note", f.note(), "status", f.status(), "now", now);
		return new ResponseModel<SupplierDTO>().success(one(id));
	}

	@Transactional
	public ResponseModel<SupplierDTO> update(SupplierDTO request) {
		BranchScope scope = branchScopeService.current();
		SupplierDTO existing = writable(scope, request.id());
		branchScopeService.assertSameOwner(existing.branchId(), request.branchId());
		Fields f = validate(request);
		sql.execute("UPDATE public.supplier SET name = :name, contact_name = :contact, phone = :phone, address = :address, note = :note, "
				+ "status = :status, modificated = :now WHERE id = :id",
				"id", existing.id(), "name", f.name(), "contact", f.contact(), "phone", f.phone(), "address", f.address(), "note", f.note(),
				"status", f.status(), "now", LocalDateTime.now());
		return new ResponseModel<SupplierDTO>().success(one(existing.id()));
	}

	@Transactional
	public ResponseModel<SupplierDTO> delete(String id) {
		SupplierDTO existing = writable(branchScopeService.current(), id);
		if (!sql.rows("SELECT 1 FROM public.purchase_order p WHERE p.supplier_id = :id LIMIT 1", "id", existing.id()).isEmpty()) {
			throw new ApiException("409", "Supplier sudah dipakai pesanan pembelian; nonaktifkan saja");
		}
		sql.execute("DELETE FROM public.supplier WHERE id = :id", "id", existing.id());
		return new ResponseModel<SupplierDTO>().success();
	}

	// Dipakai PurchaseService: supplier aktif yang terlihat oleh cabang penerima (cabang itu sendiri atau induknya).
	public SupplierDTO usable(String supplierId, java.util.Set<String> branchAndAncestors) {
		SupplierDTO supplier = supplierId == null ? null : one(supplierId.trim());
		if (supplier == null || !branchAndAncestors.contains(supplier.branchId())) {
			throw new ApiException("404", "Supplier tidak ditemukan");
		}
		if (!"ACTIVE".equals(supplier.status())) {
			throw new ApiException("409", "Supplier nonaktif");
		}
		return supplier;
	}

	private SupplierDTO writable(BranchScope scope, String id) {
		SupplierDTO existing = id == null ? null : one(id.trim());
		if (existing == null || !scope.contains(existing.branchId())) {
			throw new ApiException("404", "Supplier tidak ditemukan");
		}
		return existing;
	}

	SupplierDTO one(String id) {
		List<Object[]> rows = sql.rows("SELECT s.id, s.serial, s.branch_id, b.name, s.name, s.contact_name, s.phone, s.address, s.note, s.status "
				+ "FROM public.supplier s JOIN public.branch b ON b.id = s.branch_id WHERE s.id = :id", "id", id);
		return rows.isEmpty() ? null : dto(rows.get(0));
	}

	private SupplierDTO dto(Object[] r) {
		return new SupplierDTO(str(r[0]), str(r[1]), str(r[2]), str(r[3]), str(r[4]), str(r[5]), str(r[6]), str(r[7]), str(r[8]), str(r[9]));
	}

	private record Fields(String name, String contact, String phone, String address, String note, String status) {
	}

	private Fields validate(SupplierDTO request) {
		String name = clean(request.name());
		if (name == null) {
			throw new ApiException("400", "Nama supplier tidak boleh kosong");
		}
		String status = clean(request.status()) == null ? "ACTIVE" : request.status().trim();
		if (!status.equals("ACTIVE") && !status.equals("INACTIVE")) {
			throw new ApiException("400", "Status supplier tidak valid");
		}
		return new Fields(limit(name, 256, "Nama"), limit(clean(request.contactName()), 128, "Nama kontak"),
				limit(clean(request.phone()), 32, "Telepon"), limit(clean(request.address()), 500, "Alamat"),
				limit(clean(request.note()), 500, "Catatan"), status);
	}

	private String limit(String value, int max, String label) {
		if (value != null && value.length() > max) {
			throw new ApiException("400", label + " tidak boleh lebih dari " + max + " karakter");
		}
		return value;
	}
}
