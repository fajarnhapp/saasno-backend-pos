package com.saasno.service;

import static com.saasno.service.AnalyticsSupport.date;
import static com.saasno.service.AnalyticsSupport.dec;
import static com.saasno.service.AnalyticsSupport.lng;
import static com.saasno.service.AnalyticsSupport.str;
import static com.saasno.service.SqlHelper.clean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.entity.ProductEntity;
import com.saasno.entity.StockMovementEntity;
import com.saasno.repository.ConfigurationRepository;
import com.saasno.utility.BranchScope;
import com.saasno.utility.constant.ConfigurationKey;
import com.saasno.utility.dto.batch.BatchDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.generator.ID;

// Batch stok untuk produk yang dilacak kedaluwarsanya (FEFO: yang paling cepat kedaluwarsa keluar duluan). Semua perubahan stok
// lewat StockService.applyMovement, yang memanggil onChange di sini. Penjualan dan oper stok tidak mengambil batch yang sudah
// kedaluwarsa (barangnya harus dibuang, bukan dijual). Stok tanpa batch (stok lama atau retur) dijual setelah batch habis.
@Service
public class BatchService {

	private static final int MAX_BATCH_NO = 64;
	private static final int DEFAULT_WARNING_DAYS = 30;
	private static final int MAX_PAGE = 100;
	private static final ThreadLocal<String[]> HINT = new ThreadLocal<>();
	private static final ThreadLocal<String> FORCED = new ThreadLocal<>();

	@Autowired
	private SqlHelper sql;

	@Autowired
	private ConfigurationRepository configurationRepository;

	@Autowired
	private BranchScopeService branchScopeService;

	// ------------------------------------------------------------------ petunjuk dari pemanggil

	// Menjalankan action dengan batch/kedaluwarsa untuk stok masuk di dalamnya (mis. penerimaan barang).
	public void withBatch(String batchNo, LocalDate expiry, Runnable action) {
		HINT.set(new String[] { clean(batchNo), expiry == null ? null : expiry.toString() });
		try {
			action.run();
		} finally {
			HINT.remove();
		}
	}

	// Menjalankan action yang pengurangan stoknya harus mengambil batch tertentu (pembuangan batch kedaluwarsa).
	public void withForcedBatch(String batchId, Runnable action) {
		FORCED.set(batchId);
		try {
			action.run();
		} finally {
			FORCED.remove();
		}
	}

	public <T> T withBatch(String batchNo, LocalDate expiry, Supplier<T> action) {
		HINT.set(new String[] { clean(batchNo), expiry == null ? null : expiry.toString() });
		try {
			return action.get();
		} finally {
			HINT.remove();
		}
	}

	// ------------------------------------------------------------------ dipanggil StockService.applyMovement

	public void onChange(String branchId, ProductEntity product, BigDecimal delta, String type) {
		if (!product.isTrackExpiry() || StockMovementEntity.TRANSFER_IN.equals(type) || StockMovementEntity.TRANSFER_OUT.equals(type)) {
			return;
		}
		if (delta.signum() > 0) {
			increase(branchId, product, delta, type);
		} else if (delta.signum() < 0) {
			decrease(branchId, product, delta.negate(), StockMovementEntity.SALE.equals(type));
		}
	}

	private void increase(String branchId, ProductEntity product, BigDecimal quantity, String type) {
		String[] hint = HINT.get();
		if (hint == null || hint[1] == null) {
			if (StockMovementEntity.RECEIVE.equals(type)) {
				throw new ApiException("400", "Tanggal kedaluwarsa wajib diisi untuk " + product.getSerial() + " " + product.getName());
			}
			return; // retur/koreksi naik: masuk sebagai stok tanpa batch
		}
		String batchNo = hint[0];
		if (batchNo != null && batchNo.length() > MAX_BATCH_NO) {
			throw new ApiException("400", "Nomor batch tidak boleh lebih dari " + MAX_BATCH_NO + " karakter");
		}
		add(branchId, product.getId(), batchNo, LocalDate.parse(hint[1]), quantity);
	}

	private void add(String branchId, String productId, String batchNo, LocalDate expiry, BigDecimal quantity) {
		List<Object[]> existing = sql.rows("SELECT b.id FROM public.stock_batch b WHERE b.branch_id = :branch AND b.product_id = :product AND b.expiry_date = :expiry "
				+ "AND COALESCE(b.batch_no, '') = COALESCE(:batchNo, '') FOR UPDATE", SqlHelper.map("branch", branchId, "product", productId, "expiry", expiry, "batchNo", batchNo));
		if (!existing.isEmpty()) {
			sql.execute("UPDATE public.stock_batch SET quantity = quantity + :q WHERE id = :id", "q", quantity, "id", str(existing.get(0)[0]));
			return;
		}
		sql.execute("INSERT INTO public.stock_batch (id, branch_id, product_id, batch_no, expiry_date, quantity, received_at) VALUES (:id, :branch, :product, :batchNo, :expiry, :q, :now)",
				SqlHelper.map("id", ID.uuid(), "branch", branchId, "product", productId, "batchNo", batchNo, "expiry", expiry, "q", quantity, "now", LocalDateTime.now()));
	}

	// Mengambil batch menurut FEFO. blockExpired: batch yang sudah kedaluwarsa tidak boleh diambil (penjualan).
	private List<Object[]> decrease(String branchId, ProductEntity product, BigDecimal need, boolean blockExpired) {
		String forced = FORCED.get();
		LocalDate today = LocalDate.now();
		List<Object[]> taken = new ArrayList<>();
		if (forced != null) {
			List<Object[]> rows = sql.rows("SELECT b.quantity, b.batch_no, b.expiry_date FROM public.stock_batch b WHERE b.id = :id AND b.branch_id = :branch AND b.product_id = :product FOR UPDATE",
					SqlHelper.map("id", forced, "branch", branchId, "product", product.getId()));
			if (rows.isEmpty() || dec(rows.get(0)[0]).compareTo(need) < 0) {
				throw new ApiException("409", "Jumlah melebihi isi batch");
			}
			reduce(forced, dec(rows.get(0)[0]), need);
			taken.add(new Object[] { need, rows.get(0)[1], rows.get(0)[2] });
			return taken;
		}
		BigDecimal balance = dec(sql.rows("SELECT COALESCE((SELECT quantity FROM public.stock_balance WHERE branch_id = :branch AND product_id = :product), 0)",
				"branch", branchId, "product", product.getId()).get(0)[0]);
		List<Object[]> batches = sql.rows("SELECT b.id, b.quantity, b.batch_no, b.expiry_date FROM public.stock_batch b WHERE b.branch_id = :branch AND b.product_id = :product "
				+ "ORDER BY b.expiry_date, b.received_at FOR UPDATE", "branch", branchId, "product", product.getId());
		BigDecimal expired = BigDecimal.ZERO;
		for (Object[] b : batches) {
			if (date(b[3]).isBefore(today)) {
				expired = expired.add(dec(b[1]));
			}
		}
		if (blockExpired && need.compareTo(balance.subtract(expired)) > 0) {
			throw new ApiException("409", "Stok " + product.getSerial() + " " + product.getName() + " yang masih layak jual tidak cukup (sebagian sudah kedaluwarsa)");
		}
		BigDecimal remaining = need;
		for (Object[] b : batches) {
			if (remaining.signum() <= 0) {
				break;
			}
			if (blockExpired && date(b[3]).isBefore(today)) {
				continue;
			}
			BigDecimal take = dec(b[1]).min(remaining);
			reduce(str(b[0]), dec(b[1]), take);
			taken.add(new Object[] { take, b[2], b[3] });
			remaining = remaining.subtract(take);
		}
		return taken; // sisa (remaining) diambil dari stok tanpa batch
	}

	private void reduce(String batchId, BigDecimal current, BigDecimal take) {
		if (current.compareTo(take) == 0) {
			sql.execute("DELETE FROM public.stock_batch WHERE id = :id", "id", batchId);
		} else {
			sql.execute("UPDATE public.stock_batch SET quantity = quantity - :q WHERE id = :id", "q", take, "id", batchId);
		}
	}

	// Oper stok: batch yang diambil di cabang asal (FEFO, tanpa yang kedaluwarsa) dipindah apa adanya ke cabang tujuan.
	public void transfer(String sourceBranchId, String destinationBranchId, ProductEntity product, BigDecimal quantity) {
		if (!product.isTrackExpiry()) {
			return;
		}
		for (Object[] t : decrease(sourceBranchId, product, quantity, true)) {
			add(destinationBranchId, product.getId(), str(t[1]), date(t[2]), dec(t[0]));
		}
	}

	@Transactional
	public void clear(String productId) {
		sql.execute("DELETE FROM public.stock_batch WHERE product_id = :product", "product", productId);
	}

	// ------------------------------------------------------------------ baca

	public int warningDays() {
		try {
			int value = Integer.parseInt(String.valueOf(configurationRepository.get(ConfigurationKey.EXPIRY_WARNING_DAYS)).trim());
			return value < 1 ? DEFAULT_WARNING_DAYS : Math.min(value, 365);
		} catch (NumberFormatException e) {
			return DEFAULT_WARNING_DAYS;
		}
	}

	private String scopeFilter(BranchScope scope, String wantedBranch, Map<String, Object> params) {
		if (wantedBranch != null) {
			if (!scope.contains(wantedBranch)) {
				throw new ApiException("404", "Cabang tidak ditemukan");
			}
			params.put("wanted", wantedBranch);
			return " AND b.branch_id = :wanted";
		}
		if (scope.isGlobal()) {
			return "";
		}
		params.put("scope", scope.getIds());
		return " AND b.branch_id IN (:scope)";
	}

	@Transactional(readOnly = true)
	public BatchDTO.Summary summary(BranchScope scope, String branchId) {
		Map<String, Object> params = new HashMap<>();
		String filter = scopeFilter(scope, clean(branchId), params);
		int days = warningDays();
		params.put("today", LocalDate.now());
		params.put("soon", LocalDate.now().plusDays(days));
		Object[] r = sql.rows("SELECT COUNT(*) FILTER (WHERE b.expiry_date < :today), COALESCE(SUM(b.quantity) FILTER (WHERE b.expiry_date < :today), 0), "
				+ "COALESCE(SUM(b.quantity * COALESCE(p.cost_price, 0)) FILTER (WHERE b.expiry_date < :today), 0), "
				+ "COUNT(*) FILTER (WHERE b.expiry_date >= :today AND b.expiry_date <= :soon), "
				+ "COALESCE(SUM(b.quantity * COALESCE(p.cost_price, 0)) FILTER (WHERE b.expiry_date >= :today AND b.expiry_date <= :soon), 0) "
				+ "FROM public.stock_batch b JOIN public.product p ON p.id = b.product_id WHERE 1 = 1" + filter, params).get(0);
		return new BatchDTO.Summary(lng(r[0]), dec(r[1]), dec(r[2]), lng(r[3]), dec(r[4]), days);
	}

	@Transactional(readOnly = true)
	public BatchDTO.Page read(String status, String name, String branchId, Integer offset, Integer limit) {
		BranchScope scope = branchScopeService.current();
		Map<String, Object> params = new HashMap<>();
		StringBuilder where = new StringBuilder("1 = 1").append(scopeFilter(scope, clean(branchId), params));
		LocalDate today = LocalDate.now();
		int days = warningDays();
		params.put("today", today);
		params.put("soon", today.plusDays(days));
		String wanted = clean(status) == null ? "ALL" : status.trim().toUpperCase();
		switch (wanted) {
		case "EXPIRED" -> where.append(" AND b.expiry_date < :today");
		case "SOON" -> where.append(" AND b.expiry_date >= :today AND b.expiry_date <= :soon");
		case "ALL" -> {
		}
		default -> throw new ApiException("400", "Status batch tidak valid");
		}
		if (clean(name) != null) {
			where.append(" AND (LOWER(p.name) LIKE :name OR LOWER(p.serial) LIKE :name OR LOWER(COALESCE(b.batch_no, '')) LIKE :name)");
			params.put("name", "%" + name.trim().toLowerCase() + "%");
		}
		long total = lng(sql.rows("SELECT COUNT(*) FROM public.stock_batch b JOIN public.product p ON p.id = b.product_id WHERE " + where, params).get(0)[0]);
		int size = limit == null || limit < 1 ? 10 : Math.min(limit, MAX_PAGE);
		int start = offset == null || offset < 0 ? 0 : offset;
		List<BatchDTO> list = new ArrayList<>();
		for (Object[] r : sql.rows("SELECT b.id, b.branch_id, br.name, p.id, p.serial, p.name, p.unit, b.batch_no, b.expiry_date, b.quantity, "
				+ "b.quantity * COALESCE(p.cost_price, 0) FROM public.stock_batch b JOIN public.product p ON p.id = b.product_id JOIN public.branch br ON br.id = b.branch_id "
				+ "WHERE " + where + " ORDER BY b.expiry_date, p.name LIMIT " + size + " OFFSET " + start, params)) {
			LocalDate expiry = date(r[8]);
			long left = ChronoUnit.DAYS.between(today, expiry);
			list.add(new BatchDTO(str(r[0]), str(r[1]), str(r[2]), str(r[3]), str(r[4]), str(r[5]), str(r[6]), str(r[7]), expiry, dec(r[9]), dec(r[10]), left,
					left < 0 ? "EXPIRED" : left <= days ? "SOON" : "OK"));
		}
		return new BatchDTO.Page(list, total, summary(scope, branchId));
	}

	public BatchDTO one(String id, BranchScope scope) {
		List<Object[]> rows = id == null ? List.of() : sql.rows("SELECT b.branch_id, b.product_id, b.quantity, b.batch_no, b.expiry_date FROM public.stock_batch b WHERE b.id = :id", "id", id.trim());
		if (rows.isEmpty() || !scope.contains(str(rows.get(0)[0]))) {
			throw new ApiException("404", "Batch tidak ditemukan");
		}
		Object[] r = rows.get(0);
		return new BatchDTO(id.trim(), str(r[0]), null, str(r[1]), null, null, null, str(r[3]), date(r[4]), dec(r[2]), null, 0, null);
	}
}
