package com.saasno.service;

import static com.saasno.service.AnalyticsSupport.date;
import static com.saasno.service.AnalyticsSupport.dec;
import static com.saasno.service.AnalyticsSupport.lng;
import static com.saasno.service.AnalyticsSupport.str;
import static com.saasno.service.SqlHelper.clean;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.utility.BranchScope;
import com.saasno.utility.dto.loyalty.LoyaltyDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.generator.ID;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataResult;

// Voucher potongan. Milik satu cabang: berlaku di cabang itu dan turunannya; diubah hanya oleh cabang pemilik dan induknya.
// Kode unik di seluruh sistem (huruf besar, angka, dan - atau _). Voucher yang sudah pernah dipakai tidak dihapus, hanya dinonaktifkan.
@Service
public class VoucherService {

	private static final int MAX_PAGE = 100;
	private static final BigDecimal MAX_VALUE = new BigDecimal("9999999999.99");

	@Autowired
	private SqlHelper sql;

	@Autowired
	private BranchScopeService branchScopeService;

	private static final String SELECT = "SELECT v.id, v.branch_id, b.name, v.code, v.name, v.type, v.value, v.max_discount, v.min_purchase, v.max_uses, v.used_count, "
			+ "v.customer_id, c.pic, v.valid_from, v.valid_until, v.status FROM public.voucher v JOIN public.branch b ON b.id = v.branch_id "
			+ "LEFT JOIN public.customer c ON c.id = v.customer_id";

	@Transactional(readOnly = true)
	public ResponseEntity<DataResult<LoyaltyDTO.Voucher>> read(String name, String status, Integer offset, Integer limit) {
		BranchScope scope = branchScopeService.current();
		StringBuilder where = new StringBuilder("1 = 1");
		Map<String, Object> params = new HashMap<>();
		if (!scope.isGlobal()) {
			where.append(" AND v.branch_id IN (:visible)");
			params.put("visible", scope.getVisibleIds());
		}
		if (clean(name) != null) {
			where.append(" AND (LOWER(v.code) LIKE :name OR LOWER(v.name) LIKE :name)");
			params.put("name", "%" + name.trim().toLowerCase() + "%");
		}
		if (clean(status) != null) {
			where.append(" AND v.status = :status");
			params.put("status", status.trim());
		}
		long total = lng(sql.rows("SELECT COUNT(*) FROM public.voucher v WHERE " + where, params).get(0)[0]);
		int size = limit == null || limit < 1 ? 10 : Math.min(limit, MAX_PAGE);
		int start = offset == null || offset < 0 ? 0 : offset;
		List<LoyaltyDTO.Voucher> list = new ArrayList<>();
		for (Object[] r : sql.rows(SELECT + " WHERE " + where + " ORDER BY v.registered DESC LIMIT " + size + " OFFSET " + start, params)) {
			list.add(dto(r));
		}
		return ResponseEntity.ok(new DataResult<>(list, BigInteger.valueOf(total), BigInteger.valueOf(total)));
	}

	@Transactional
	public ResponseModel<LoyaltyDTO.Voucher> create(LoyaltyDTO.Voucher request) {
		BranchScope scope = branchScopeService.current();
		String owner = branchScopeService.resolveOwner(scope, request.branchId());
		Fields f = validate(request, owner);
		String id = ID.uuid();
		LocalDateTime now = LocalDateTime.now();
		assertCodeFree(f.code(), null);
		{
			sql.execute("INSERT INTO public.voucher (id, branch_id, code, name, type, value, max_discount, min_purchase, max_uses, used_count, customer_id, valid_from, valid_until, status, registered, modificated) "
					+ "VALUES (:id, :branch, :code, :name, :type, :value, :maxDiscount, :minPurchase, :maxUses, 0, :customer, :from, :until, :status, :now, :now)",
					SqlHelper.map("id", id, "branch", owner, "code", f.code(), "name", f.name(), "type", f.type(), "value", f.value(), "maxDiscount", f.maxDiscount(),
							"minPurchase", f.minPurchase(), "maxUses", f.maxUses(), "customer", f.customerId(), "from", f.from(), "until", f.until(), "status", f.status(), "now", now));
		}
		return new ResponseModel<LoyaltyDTO.Voucher>().success(one(id));
	}

	@Transactional
	public ResponseModel<LoyaltyDTO.Voucher> update(LoyaltyDTO.Voucher request) {
		BranchScope scope = branchScopeService.current();
		LoyaltyDTO.Voucher existing = writable(scope, request.id());
		branchScopeService.assertSameOwner(existing.branchId(), request.branchId());
		Fields f = validate(request, existing.branchId());
		// Kode dan jenis tidak diubah setelah dipakai: transaksi lama merujuknya.
		if (existing.usedCount() > 0 && (!existing.code().equals(f.code()) || !existing.type().equals(f.type()))) {
			throw new ApiException("409", "Kode dan jenis voucher yang sudah dipakai tidak dapat diubah");
		}
		if (f.maxUses() != null && f.maxUses() < existing.usedCount()) {
			throw new ApiException("400", "Batas pemakaian tidak boleh kurang dari yang sudah terpakai (" + existing.usedCount() + ")");
		}
		assertCodeFree(f.code(), existing.id());
		{
			sql.execute("UPDATE public.voucher SET code = :code, name = :name, type = :type, value = :value, max_discount = :maxDiscount, min_purchase = :minPurchase, max_uses = :maxUses, "
					+ "customer_id = :customer, valid_from = :from, valid_until = :until, status = :status, modificated = :now WHERE id = :id",
					SqlHelper.map("id", existing.id(), "code", f.code(), "name", f.name(), "type", f.type(), "value", f.value(), "maxDiscount", f.maxDiscount(),
							"minPurchase", f.minPurchase(), "maxUses", f.maxUses(), "customer", f.customerId(), "from", f.from(), "until", f.until(), "status", f.status(),
							"now", LocalDateTime.now()));
		}
		return new ResponseModel<LoyaltyDTO.Voucher>().success(one(existing.id()));
	}

	@Transactional
	public ResponseModel<LoyaltyDTO.Voucher> delete(String id) {
		LoyaltyDTO.Voucher existing = writable(branchScopeService.current(), id);
		if (existing.usedCount() > 0 || !sql.rows("SELECT 1 FROM public.sale s WHERE s.voucher_id = :id LIMIT 1", "id", existing.id()).isEmpty()) {
			throw new ApiException("409", "Voucher sudah pernah dipakai; nonaktifkan saja");
		}
		sql.execute("DELETE FROM public.voucher WHERE id = :id", "id", existing.id());
		return new ResponseModel<LoyaltyDTO.Voucher>().success();
	}

	// Kode harus unik; dikunci supaya dua pembuatan bersamaan dengan kode sama tidak keduanya lolos.
	private void assertCodeFree(String code, String exceptId) {
		sql.lock("voucher_code:" + code);
		List<Object[]> rows = sql.rows("SELECT v.id FROM public.voucher v WHERE v.code = :code", "code", code);
		if (!rows.isEmpty() && !str(rows.get(0)[0]).equals(exceptId)) {
			throw new ApiException("409", "Kode voucher " + code + " sudah dipakai");
		}
	}

	private LoyaltyDTO.Voucher writable(BranchScope scope, String id) {
		LoyaltyDTO.Voucher existing = id == null ? null : one(id.trim());
		if (existing == null || !scope.contains(existing.branchId())) {
			throw new ApiException("404", "Voucher tidak ditemukan");
		}
		return existing;
	}

	private LoyaltyDTO.Voucher one(String id) {
		List<Object[]> rows = sql.rows(SELECT + " WHERE v.id = :id", "id", id);
		return rows.isEmpty() ? null : dto(rows.get(0));
	}

	private LoyaltyDTO.Voucher dto(Object[] r) {
		return new LoyaltyDTO.Voucher(str(r[0]), str(r[1]), str(r[2]), str(r[3]), str(r[4]), str(r[5]), dec(r[6]), r[7] == null ? null : dec(r[7]), dec(r[8]),
				r[9] == null ? null : ((Number) r[9]).intValue(), ((Number) r[10]).intValue(), str(r[11]), str(r[12]), r[13] == null ? null : date(r[13]),
				r[14] == null ? null : date(r[14]), str(r[15]));
	}

	private record Fields(String code, String name, String type, BigDecimal value, BigDecimal maxDiscount, BigDecimal minPurchase, Integer maxUses, String customerId,
			LocalDate from, LocalDate until, String status) {
	}

	private Fields validate(LoyaltyDTO.Voucher r, String ownerBranch) {
		String code = clean(r.code()) == null ? null : r.code().trim().toUpperCase();
		if (code == null || !code.matches("[A-Z0-9_-]{3,32}")) {
			throw new ApiException("400", "Kode voucher 3-32 karakter: huruf, angka, - atau _");
		}
		String name = clean(r.name());
		if (name == null || name.length() > 128) {
			throw new ApiException("400", "Nama voucher wajib diisi (maksimal 128 karakter)");
		}
		String type = clean(r.type()) == null ? "" : r.type().trim().toUpperCase();
		if (!type.equals("FIXED") && !type.equals("PERCENT")) {
			throw new ApiException("400", "Jenis voucher harus FIXED atau PERCENT");
		}
		BigDecimal value = r.value();
		if (value == null || value.signum() <= 0 || value.compareTo(MAX_VALUE) > 0 || (type.equals("PERCENT") && value.compareTo(new BigDecimal("100")) > 0)
				|| (type.equals("FIXED") && value.stripTrailingZeros().scale() > 0)) {
			throw new ApiException("400", type.equals("PERCENT") ? "Persen voucher harus lebih dari 0 sampai 100" : "Nilai voucher harus rupiah bulat lebih dari 0");
		}
		BigDecimal maxDiscount = type.equals("PERCENT") ? r.maxDiscount() : null;
		if (maxDiscount != null && (maxDiscount.signum() <= 0 || maxDiscount.stripTrailingZeros().scale() > 0)) {
			throw new ApiException("400", "Batas potongan harus rupiah bulat lebih dari 0");
		}
		BigDecimal minPurchase = r.minPurchase() == null ? BigDecimal.ZERO : r.minPurchase();
		if (minPurchase.signum() < 0 || minPurchase.stripTrailingZeros().scale() > 0) {
			throw new ApiException("400", "Belanja minimal harus rupiah bulat 0 atau lebih");
		}
		if (r.maxUses() != null && r.maxUses() < 1) {
			throw new ApiException("400", "Batas pemakaian harus minimal 1 (kosong = tanpa batas)");
		}
		if (r.validFrom() != null && r.validUntil() != null && r.validUntil().isBefore(r.validFrom())) {
			throw new ApiException("400", "Tanggal berakhir tidak boleh sebelum tanggal mulai");
		}
		String customerId = clean(r.customerId());
		if (customerId != null) {
			List<Object[]> customer = sql.rows("SELECT c.branch_id FROM public.customer c WHERE c.id = :id", "id", customerId);
			if (customer.isEmpty() || !branchScopeService.subtree(ownerBranch).contains(str(customer.get(0)[0]))) {
				throw new ApiException("404", "Customer tidak ditemukan");
			}
		}
		String status = clean(r.status()) == null ? "ACTIVE" : r.status().trim().toUpperCase();
		if (!status.equals("ACTIVE") && !status.equals("INACTIVE")) {
			throw new ApiException("400", "Status voucher tidak valid");
		}
		return new Fields(code, name, type, value, maxDiscount, minPurchase, r.maxUses(), customerId, r.validFrom(), r.validUntil(), status);
	}
}
