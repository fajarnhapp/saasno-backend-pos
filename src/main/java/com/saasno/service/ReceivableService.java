package com.saasno.service;

import static com.saasno.service.AnalyticsSupport.date;
import static com.saasno.service.AnalyticsSupport.dec;
import static com.saasno.service.AnalyticsSupport.lng;
import static com.saasno.service.AnalyticsSupport.str;
import static com.saasno.service.AnalyticsSupport.time;
import static com.saasno.service.SqlHelper.clean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.entity.BranchEntity;
import com.saasno.entity.SaleEntity;
import com.saasno.repository.BranchRepository;
import com.saasno.repository.ConfigurationRepository;
import com.saasno.utility.BranchScope;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.constant.ConfigurationKey;
import com.saasno.utility.dto.receivable.ReceivableDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.generator.ID;
import com.saasno.utility.model.ResponseModel;

// Piutang customer dari penjualan tempo. Bagian yang belum dibayar saat transaksi (sale.credit_amount) menjadi piutang dengan jatuh
// tempo; dilunasi bertahap lewat pembayaran yang masuk ke shift penerimanya. Retur mengurangi piutang lebih dulu sebelum uang
// dikembalikan. Void hanya boleh bila belum ada pembayaran selain uang muka. Piutang tak tertagih dihapus dengan alasan (tidak
// membuat jurnal sendiri; penjualannya tetap tercatat sebagai omzet).
@Service
public class ReceivableService {

	private static final int MAX_PAGE = 100;
	private static final int MAX_DUE_DAYS = 365;
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyMMdd");
	private static final String SELECT = "SELECT r.id, r.serial, r.sale_id, s.serial, r.customer_id, c.pic, "
			+ "(SELECT ct.value FROM public.customer_contact cc JOIN public.contact ct ON ct.id = cc.contact_id WHERE cc.customer_id = r.customer_id AND UPPER(ct.type) NOT LIKE '%MAIL%' LIMIT 1), "
			+ "r.branch_id, b.name, r.amount, r.paid_amount, r.returned_amount, r.due_date, r.status, r.note, u.username, r.registered, r.closed_at "
			+ "FROM public.receivable r JOIN public.sale s ON s.id = r.sale_id JOIN public.customer c ON c.id = r.customer_id JOIN public.branch b ON b.id = r.branch_id "
			+ "JOIN public.\"user\" u ON u.id = r.created_by";

	@Autowired
	private SqlHelper sql;

	@Autowired
	private BranchScopeService branchScopeService;

	@Autowired
	private BranchRepository branchRepository;

	@Autowired
	private ConfigurationRepository configurationRepository;

	@Autowired
	private SessionUtility sessionUtility;

	@Autowired
	private ShiftService shiftService;

	// ------------------------------------------------------------------ pengaturan & penjagaan

	public BigDecimal creditLimit() {
		try {
			BigDecimal value = new BigDecimal(String.valueOf(configurationRepository.get(ConfigurationKey.CREDIT_LIMIT)).trim());
			return value.signum() < 0 ? BigDecimal.ZERO : value;
		} catch (NumberFormatException e) {
			return BigDecimal.ZERO;
		}
	}

	private int defaultDays() {
		try {
			int value = Integer.parseInt(String.valueOf(configurationRepository.get(ConfigurationKey.CREDIT_DEFAULT_DAYS)).trim());
			return value < 1 ? 14 : Math.min(value, MAX_DUE_DAYS);
		} catch (NumberFormatException e) {
			return 14;
		}
	}

	// Jatuh tempo yang berlaku: yang diminta (maksimal setahun ke depan) atau bawaan.
	public LocalDate dueDate(LocalDate requested) {
		LocalDate today = LocalDate.now();
		if (requested == null) {
			return today.plusDays(defaultDays());
		}
		if (requested.isBefore(today) || requested.isAfter(today.plusDays(MAX_DUE_DAYS))) {
			throw new ApiException("400", "Jatuh tempo harus antara hari ini dan " + MAX_DUE_DAYS + " hari ke depan");
		}
		return requested;
	}

	// Piutang berjalan (belum lunas) seorang customer.
	public BigDecimal outstanding(String customerId) {
		return dec(sql.rows("SELECT COALESCE(SUM(r.amount - r.paid_amount - r.returned_amount), 0) FROM public.receivable r WHERE r.customer_id = :c AND r.status = 'OPEN'", "c", customerId).get(0)[0]);
	}

	// Penjualan tempo hanya bila CREDIT_LIMIT > 0 dan total piutang berjalan customer setelah ini tidak melebihinya.
	public void assertWithinLimit(String customerId, BigDecimal newCredit) {
		BigDecimal limit = creditLimit();
		if (limit.signum() == 0) {
			throw new ApiException("409", "Penjualan tempo belum diaktifkan (atur CREDIT_LIMIT di Konfigurasi)");
		}
		sql.lock("credit_customer:" + customerId);
		BigDecimal outstanding = dec(sql.rows("SELECT COALESCE(SUM(r.amount - r.paid_amount - r.returned_amount), 0) FROM public.receivable r WHERE r.customer_id = :c AND r.status = 'OPEN'",
				"c", customerId).get(0)[0]);
		if (outstanding.add(newCredit).compareTo(limit) > 0) {
			throw new ApiException("409", "Melebihi batas piutang customer (batas Rp " + limit.stripTrailingZeros().toPlainString() + ", piutang berjalan Rp "
					+ outstanding.stripTrailingZeros().toPlainString() + ")");
		}
	}

	// ------------------------------------------------------------------ dipanggil penjualan, retur, void

	public void open(SaleEntity sale, LocalDate dueDate) {
		BranchEntity branch = branchRepository.find(sale.getBranchId());
		String prefix = "PIU/" + branch.getSerial() + "/" + LocalDate.now().format(DAY) + "/";
		sql.lock("receivable_serial:" + prefix);
		List<Object[]> latest = sql.rows("SELECT MAX(r.serial) FROM public.receivable r WHERE r.serial LIKE :prefix", "prefix", prefix + "%");
		int sequence = latest.isEmpty() || latest.get(0)[0] == null ? 1 : Integer.parseInt(str(latest.get(0)[0]).substring(prefix.length())) + 1;
		sql.execute("INSERT INTO public.receivable (id, serial, sale_id, customer_id, branch_id, amount, paid_amount, returned_amount, due_date, status, created_by, registered) "
				+ "VALUES (:id, :serial, :sale, :customer, :branch, :amount, 0, 0, :due, 'OPEN', :user, :now)",
				SqlHelper.map("id", ID.uuid(), "serial", prefix + String.format("%03d", sequence), "sale", sale.getId(), "customer", sale.getCustomerId(),
						"branch", sale.getBranchId(), "amount", sale.getCreditAmount(), "due", dueDate, "user", sessionUtility.getUserId(), "now", LocalDateTime.now()));
	}

	// Void ditolak bila piutangnya sudah menerima pembayaran (uang yang sudah masuk tidak bisa dikembalikan otomatis).
	public void assertVoidable(SaleEntity sale) {
		if (sale.getCreditAmount().signum() == 0) {
			return;
		}
		List<Object[]> rows = sql.rows("SELECT r.paid_amount, r.status FROM public.receivable r WHERE r.sale_id = :sale", "sale", sale.getId());
		if (!rows.isEmpty() && (dec(rows.get(0)[0]).signum() > 0 || "WRITTEN_OFF".equals(str(rows.get(0)[1])))) {
			throw new ApiException("409", "Transaksi tempo ini sudah menerima pembayaran piutang atau sudah dihapus, sehingga tidak dapat di-void");
		}
	}

	// Transaksi tempo ditolak/dibatalkan/di-void: piutangnya ikut batal.
	public void voidFor(SaleEntity sale) {
		if (sale.getCreditAmount().signum() > 0) {
			sql.execute("UPDATE public.receivable SET status = 'VOID', closed_at = :now WHERE sale_id = :sale AND status = 'OPEN'", "sale", sale.getId(), "now", LocalDateTime.now());
		}
	}

	// Retur transaksi tempo: refund mengurangi piutang yang masih terbuka lebih dulu. Mengembalikan bagian yang diambil dari piutang.
	public BigDecimal applyReturn(SaleEntity sale, BigDecimal refund) {
		if (sale.getCreditAmount().signum() == 0) {
			return BigDecimal.ZERO;
		}
		List<Object[]> rows = sql.rows("SELECT r.id, r.amount - r.paid_amount - r.returned_amount FROM public.receivable r WHERE r.sale_id = :sale AND r.status = 'OPEN' FOR UPDATE", "sale", sale.getId());
		if (rows.isEmpty()) {
			return BigDecimal.ZERO;
		}
		BigDecimal offset = dec(rows.get(0)[1]).min(refund);
		if (offset.signum() > 0) {
			sql.execute("UPDATE public.receivable SET returned_amount = returned_amount + :o, status = CASE WHEN amount - paid_amount - returned_amount - :o = 0 THEN 'PAID' ELSE status END, "
					+ "closed_at = CASE WHEN amount - paid_amount - returned_amount - :o = 0 THEN :now ELSE closed_at END WHERE id = :id", "o", offset, "id", str(rows.get(0)[0]), "now", LocalDateTime.now());
		}
		return offset;
	}

	// ------------------------------------------------------------------ baca

	@Transactional(readOnly = true)
	public ReceivableDTO.Page read(String status, String name, String branchId, Integer offset, Integer limit) {
		BranchScope scope = branchScopeService.current();
		Map<String, Object> params = new HashMap<>();
		StringBuilder where = new StringBuilder("1 = 1");
		String wanted = clean(branchId);
		if (wanted != null) {
			if (!scope.contains(wanted)) {
				throw new ApiException("404", "Cabang tidak ditemukan");
			}
			where.append(" AND r.branch_id = :wanted");
			params.put("wanted", wanted);
		} else if (!scope.isGlobal()) {
			where.append(" AND r.branch_id IN (:scope)");
			params.put("scope", scope.getIds());
		}
		params.put("today", LocalDate.now());
		String base = where.toString();
		String filter = clean(status) == null ? "ALL" : status.trim().toUpperCase();
		switch (filter) {
		case "OPEN" -> where.append(" AND r.status = 'OPEN'");
		case "OVERDUE" -> where.append(" AND r.status = 'OPEN' AND r.due_date < :today");
		case "PAID", "VOID", "WRITTEN_OFF" -> where.append(" AND r.status = '").append(filter).append("'");
		case "ALL" -> {
		}
		default -> throw new ApiException("400", "Status piutang tidak valid");
		}
		if (clean(name) != null) {
			where.append(" AND (LOWER(c.pic) LIKE :name OR LOWER(r.serial) LIKE :name OR LOWER(s.serial) LIKE :name)");
			params.put("name", "%" + name.trim().toLowerCase() + "%");
		}
		long total = lng(sql.rows("SELECT COUNT(*) FROM public.receivable r JOIN public.sale s ON s.id = r.sale_id JOIN public.customer c ON c.id = r.customer_id WHERE " + where, params).get(0)[0]);
		int size = limit == null || limit < 1 ? 10 : Math.min(limit, MAX_PAGE);
		int start = offset == null || offset < 0 ? 0 : offset;
		List<ReceivableDTO> list = new ArrayList<>();
		for (Object[] r : sql.rows(SELECT + " WHERE " + where + " ORDER BY CASE WHEN r.status = 'OPEN' THEN 0 ELSE 1 END, r.due_date, r.registered LIMIT " + size + " OFFSET " + start, params)) {
			list.add(dto(r, null));
		}
		Object[] s = sql.rows("SELECT COUNT(*) FILTER (WHERE r.status = 'OPEN'), COALESCE(SUM(r.amount - r.paid_amount - r.returned_amount) FILTER (WHERE r.status = 'OPEN'), 0), "
				+ "COUNT(*) FILTER (WHERE r.status = 'OPEN' AND r.due_date < :today), "
				+ "COALESCE(SUM(r.amount - r.paid_amount - r.returned_amount) FILTER (WHERE r.status = 'OPEN' AND r.due_date < :today), 0) FROM public.receivable r WHERE " + base, params).get(0);
		return new ReceivableDTO.Page(list, total, new ReceivableDTO.Summary(lng(s[0]), dec(s[1]), lng(s[2]), dec(s[3])));
	}

	// Jumlah piutang lewat jatuh tempo dalam cakupan (untuk dashboard).
	@Transactional(readOnly = true)
	public long overdueCount(BranchScope scope) {
		Map<String, Object> params = new HashMap<>();
		params.put("today", LocalDate.now());
		String filter = "";
		if (!scope.isGlobal()) {
			filter = " AND r.branch_id IN (:scope)";
			params.put("scope", scope.getIds());
		}
		return lng(sql.rows("SELECT COUNT(*) FROM public.receivable r WHERE r.status = 'OPEN' AND r.due_date < :today" + filter, params).get(0)[0]);
	}

	@Transactional(readOnly = true)
	public ResponseModel<ReceivableDTO> detail(String id) {
		return new ResponseModel<ReceivableDTO>().success(visible(branchScopeService.current(), id, true));
	}

	private ReceivableDTO visible(BranchScope scope, String id, boolean withPayments) {
		List<Object[]> rows = id == null ? List.of() : sql.rows(SELECT + " WHERE r.id = :id", "id", id.trim());
		if (rows.isEmpty() || !scope.contains(str(rows.get(0)[7]))) {
			throw new ApiException("404", "Piutang tidak ditemukan");
		}
		List<ReceivableDTO.Payment> payments = null;
		if (withPayments) {
			payments = new ArrayList<>();
			for (Object[] p : sql.rows("SELECT p.id, p.amount, p.note, u.username, p.registered FROM public.receivable_payment p JOIN public.\"user\" u ON u.id = p.received_by "
					+ "WHERE p.receivable_id = :id ORDER BY p.registered", "id", id.trim())) {
				payments.add(new ReceivableDTO.Payment(str(p[0]), dec(p[1]), str(p[2]), str(p[3]), time(p[4])));
			}
		}
		return dto(rows.get(0), payments);
	}

	private ReceivableDTO dto(Object[] r, List<ReceivableDTO.Payment> payments) {
		BigDecimal amount = dec(r[9]);
		BigDecimal paid = dec(r[10]);
		BigDecimal returned = dec(r[11]);
		LocalDate due = date(r[12]);
		String status = str(r[13]);
		long late = ChronoUnit.DAYS.between(due, LocalDate.now());
		boolean overdue = "OPEN".equals(status) && late > 0;
		return new ReceivableDTO(str(r[0]), str(r[1]), str(r[2]), str(r[3]), str(r[4]), str(r[5]), str(r[6]), str(r[7]), str(r[8]), amount, paid, returned,
				amount.subtract(paid).subtract(returned), due, status, overdue, overdue ? late : 0, str(r[14]), str(r[15]), time(r[16]), time(r[17]), payments);
	}

	// ------------------------------------------------------------------ pembayaran & penghapusan

	@Transactional
	public ResponseModel<ReceivableDTO> pay(ReceivableDTO.PayRequest request) {
		BranchScope scope = branchScopeService.current();
		ReceivableDTO current = visible(scope, request.id(), false);
		List<Object[]> locked = sql.rows("SELECT r.status, r.amount - r.paid_amount - r.returned_amount FROM public.receivable r WHERE r.id = :id FOR UPDATE", "id", current.id());
		if (!"OPEN".equals(str(locked.get(0)[0]))) {
			throw new ApiException("409", "Piutang ini sudah tidak terbuka");
		}
		BigDecimal outstanding = dec(locked.get(0)[1]);
		BigDecimal amount = request.amount();
		if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 0) {
			throw new ApiException("400", "Jumlah pembayaran harus rupiah bulat lebih dari 0");
		}
		if (amount.compareTo(outstanding) > 0) {
			throw new ApiException("409", "Jumlah melebihi sisa piutang (Rp " + outstanding.stripTrailingZeros().toPlainString() + ")");
		}
		String note = clean(request.note());
		if (note != null && note.length() > 255) {
			throw new ApiException("400", "Catatan tidak boleh lebih dari 255 karakter");
		}
		// Uang masuk ke laci: penerima harus punya shift terbuka di cabang piutang (bila shift diwajibkan).
		String userId = sessionUtility.getUserId();
		shiftService.assertOpen(userId, current.branchId());
		LocalDateTime now = LocalDateTime.now();
		sql.execute("INSERT INTO public.receivable_payment (id, receivable_id, amount, note, received_by, branch_id, registered) VALUES (:id, :r, :a, :note, :user, :branch, :now)",
				SqlHelper.map("id", ID.uuid(), "r", current.id(), "a", amount, "note", note, "user", userId, "branch", current.branchId(), "now", now));
		boolean settled = outstanding.compareTo(amount) == 0;
		sql.execute("UPDATE public.receivable SET paid_amount = paid_amount + :a, status = :status, closed_at = :closed WHERE id = :id",
				"a", amount, "status", settled ? "PAID" : "OPEN", "closed", settled ? now : null, "id", current.id());
		return new ResponseModel<ReceivableDTO>().success(visible(scope, current.id(), true));
	}

	@Transactional
	public ResponseModel<ReceivableDTO> writeOff(ReceivableDTO.WriteOffRequest request) {
		BranchScope scope = branchScopeService.current();
		ReceivableDTO current = visible(scope, request.id(), false);
		String reason = clean(request.reason());
		if (reason == null || reason.length() > 500) {
			throw new ApiException("400", "Alasan wajib diisi (maksimal 500 karakter)");
		}
		List<Object[]> locked = sql.rows("SELECT r.status FROM public.receivable r WHERE r.id = :id FOR UPDATE", "id", current.id());
		if (!"OPEN".equals(str(locked.get(0)[0]))) {
			throw new ApiException("409", "Piutang ini sudah tidak terbuka");
		}
		sql.execute("UPDATE public.receivable SET status = 'WRITTEN_OFF', note = :note, closed_at = :now WHERE id = :id", "id", current.id(), "note", reason, "now", LocalDateTime.now());
		return new ResponseModel<ReceivableDTO>().success(visible(scope, current.id(), true));
	}
}
