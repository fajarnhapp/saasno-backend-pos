package com.saasno.service;

import static com.saasno.service.AnalyticsSupport.date;
import static com.saasno.service.AnalyticsSupport.dec;
import static com.saasno.service.AnalyticsSupport.lng;
import static com.saasno.service.AnalyticsSupport.str;
import static com.saasno.service.AnalyticsSupport.time;
import static com.saasno.service.SqlHelper.clean;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.entity.BranchEntity;
import com.saasno.entity.ProductEntity;
import com.saasno.entity.StockMovementEntity;
import com.saasno.repository.BranchRepository;
import com.saasno.repository.ConfigurationRepository;
import com.saasno.utility.BranchScope;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.constant.ConfigurationKey;
import com.saasno.utility.dto.purchase.PurchaseDTO;
import com.saasno.utility.dto.purchase.PurchaseRequestDTO;
import com.saasno.utility.dto.purchase.SuggestionDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.generator.ID;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataResult;

// Pembelian: draf -> dipesan -> diterima (sebagian/penuh) | dibatalkan. Penerimaan menambah stok (mutasi RECEIVE dengan
// harga beli) dan memperbarui harga pokok produk sesuai PURCHASE_COST_METHOD. Jumlah disimpan dalam satuan dasar produk dan
// harga per satuan dasar; input boleh memakai satuan konversi. Pesanan hanya milik cabang penerima.
@Service
public class PurchaseService {

	private static final int MAX_LINES = 200;
	private static final int MAX_NOTE = 500;
	private static final int MAX_PAGE = 100;
	private static final int SUGGEST_DAYS = 14;
	private static final int HISTORY_DAYS = 30;
	private static final int MAX_SUGGESTIONS = 50;
	private static final BigDecimal MAX_COST = new BigDecimal("9999999999999.99");
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyMMdd");

	@Autowired
	private SqlHelper sql;

	@Autowired
	private SessionUtility sessionUtility;

	@Autowired
	private BranchScopeService branchScopeService;

	@Autowired
	private BranchRepository branchRepository;

	@Autowired
	private StockService stockService;

	@Autowired
	private SupplierService supplierService;

	@Autowired
	private ConfigurationRepository configurationRepository;

	@Autowired
	private PriceHistoryService priceHistory;

	@Autowired
	private BatchService batchService;

	private record Line(ProductEntity product, BigDecimal quantity, BigDecimal unitCost, java.time.LocalDate expiry, String batchNo) {
	}

	// ------------------------------------------------------------------ buat / ubah

	@Transactional
	public ResponseModel<PurchaseDTO> create(PurchaseRequestDTO request) {
		BranchScope scope = branchScopeService.current();
		BranchEntity branch = branchScopeService.ownBranch(scope, request.branchId(), "Pembelian hanya dapat dibuat di cabang Anda sendiri");
		supplierService.usable(request.supplierId(), branchRepository.findAncestorIds(branch.getId()));
		List<Line> lines = lines(branch.getId(), request.items(), true);
		String note = note(request.note());

		String prefix = "PO/" + branch.getSerial() + "/" + LocalDate.now().format(DAY) + "/";
		sql.lock("purchase_serial:" + prefix);
		List<Object[]> latest = sql.rows("SELECT MAX(p.serial) FROM public.purchase_order p WHERE p.serial LIKE :prefix", "prefix", prefix + "%");
		int sequence = latest.isEmpty() || latest.get(0)[0] == null ? 1 : Integer.parseInt(str(latest.get(0)[0]).substring(prefix.length())) + 1;
		String id = ID.uuid();
		LocalDateTime now = LocalDateTime.now();
		sql.execute("INSERT INTO public.purchase_order (id, serial, branch_id, supplier_id, status, expected_date, note, total, created_by, registered, ordered_at, modificated) "
				+ "VALUES (:id, :serial, :branch, :supplier, :status, :expected, :note, :total, :user, :now, :ordered, :now)",
				"id", id, "serial", prefix + String.format("%03d", sequence), "branch", branch.getId(), "supplier", request.supplierId().trim(),
				"status", request.order() ? "ORDERED" : "DRAFT", "expected", request.expectedDate(), "note", note, "total", total(lines),
				"user", sessionUtility.getUserId(), "now", now, "ordered", request.order() ? now : null);
		insertLines(id, lines);
		return new ResponseModel<PurchaseDTO>().success(detail(id));
	}

	@Transactional
	public ResponseModel<PurchaseDTO> update(PurchaseRequestDTO request) {
		Object[] po = lockOwn(request.id());
		if (!"DRAFT".equals(str(po[1]))) {
			throw new ApiException("409", "Hanya draf yang dapat diubah");
		}
		String branchId = str(po[0]);
		supplierService.usable(request.supplierId(), branchRepository.findAncestorIds(branchId));
		List<Line> lines = lines(branchId, request.items(), true);
		sql.execute("UPDATE public.purchase_order SET supplier_id = :supplier, expected_date = :expected, note = :note, total = :total, "
				+ "modificated = :now WHERE id = :id",
				"id", request.id().trim(), "supplier", request.supplierId().trim(), "expected", request.expectedDate(), "note", note(request.note()),
				"total", total(lines), "now", LocalDateTime.now());
		sql.execute("DELETE FROM public.purchase_order_item WHERE po_id = :id", "id", request.id().trim());
		insertLines(request.id().trim(), lines);
		if (request.order()) {
			markOrdered(request.id().trim());
		}
		return new ResponseModel<PurchaseDTO>().success(detail(request.id().trim()));
	}

	@Transactional
	public ResponseModel<PurchaseDTO> order(PurchaseRequestDTO request) {
		Object[] po = lockOwn(request.id());
		if (!"DRAFT".equals(str(po[1]))) {
			throw new ApiException("409", "Hanya draf yang dapat dipesan");
		}
		markOrdered(request.id().trim());
		return new ResponseModel<PurchaseDTO>().success(detail(request.id().trim()));
	}

	@Transactional
	public ResponseModel<PurchaseDTO> cancel(PurchaseRequestDTO request) {
		Object[] po = lockOwn(request.id());
		String status = str(po[1]);
		if (!status.equals("DRAFT") && !status.equals("ORDERED")) {
			throw new ApiException("409", "Pesanan yang sudah diterima sebagian ditutup, bukan dibatalkan");
		}
		String reason = note(request.note());
		if (status.equals("ORDERED") && reason == null) {
			throw new ApiException("400", "Alasan pembatalan wajib diisi");
		}
		LocalDateTime now = LocalDateTime.now();
		sql.execute("UPDATE public.purchase_order SET status = 'CANCELLED', closed_at = :now, close_note = :note, modificated = :now WHERE id = :id",
				"id", request.id().trim(), "now", now, "note", reason);
		return new ResponseModel<PurchaseDTO>().success(detail(request.id().trim()));
	}

	// Menutup pesanan yang baru diterima sebagian: sisa tidak akan datang.
	@Transactional
	public ResponseModel<PurchaseDTO> close(PurchaseRequestDTO request) {
		Object[] po = lockOwn(request.id());
		if (!"PARTIAL".equals(str(po[1]))) {
			throw new ApiException("409", "Hanya pesanan yang diterima sebagian yang dapat ditutup");
		}
		String reason = note(request.note());
		if (reason == null) {
			throw new ApiException("400", "Alasan penutupan wajib diisi");
		}
		LocalDateTime now = LocalDateTime.now();
		sql.execute("UPDATE public.purchase_order SET status = 'RECEIVED', closed_at = :now, close_note = :note, modificated = :now WHERE id = :id",
				"id", request.id().trim(), "now", now, "note", reason);
		return new ResponseModel<PurchaseDTO>().success(detail(request.id().trim()));
	}

	// ------------------------------------------------------------------ terima barang

	@Transactional
	public ResponseModel<PurchaseDTO> receive(PurchaseRequestDTO request) {
		Object[] po = lockOwn(request.id());
		String status = str(po[1]);
		if (!status.equals("ORDERED") && !status.equals("PARTIAL")) {
			throw new ApiException("409", "Pesanan ini tidak sedang menunggu barang");
		}
		String poId = request.id().trim();
		String branchId = str(po[0]);
		if (request.items() == null || request.items().isEmpty()) {
			throw new ApiException("400", "Pilih barang yang diterima");
		}
		Set<String> distinct = new HashSet<>();
		for (PurchaseRequestDTO.Item item : request.items()) {
			if (item == null || item.productId() == null || !distinct.add(item.productId().trim())) {
				throw new ApiException("400", "Produk tidak boleh ganda dalam satu penerimaan");
			}
		}

		// Sisa yang boleh diterima per produk, dari baris pesanan yang dikunci bersama pesanannya.
		Map<String, BigDecimal[]> open = new HashMap<>();
		for (Object[] r : sql.rows("SELECT i.product_id, i.quantity, i.received_quantity FROM public.purchase_order_item i WHERE i.po_id = :id", "id", poId)) {
			open.put(str(r[0]), new BigDecimal[] { dec(r[1]), dec(r[2]) });
		}
		List<Line> received = new ArrayList<>();
		for (PurchaseRequestDTO.Item item : request.items()) {
			if (item == null || item.productId() == null || !open.containsKey(item.productId().trim())) {
				throw new ApiException("400", "Produk tidak ada dalam pesanan ini");
			}
			ProductEntity product = stockService.visibleProduct(branchId, item.productId());
			BigDecimal quantity = stockService.toBase(product, item.quantity(), item.unit(), false);
			BigDecimal[] counts = open.get(product.getId());
			if (quantity.compareTo(counts[0].subtract(counts[1])) > 0) {
				throw new ApiException("409", "Jumlah " + product.getSerial() + " " + product.getName() + " melebihi sisa pesanan");
			}
			received.add(new Line(product, quantity, baseCost(item.unitCost(), item.quantity(), quantity), item.expiryDate(), clean(item.batchNo())));
		}
		received.sort(Comparator.comparing(l -> l.product().getId()));

		String note = note(request.note());
		String invoice = clean(request.invoiceNo());
		if (invoice != null && invoice.length() > 64) {
			throw new ApiException("400", "Nomor faktur tidak boleh lebih dari 64 karakter");
		}
		BranchEntity branch = branchRepository.find(branchId);
		String prefix = "GR/" + branch.getSerial() + "/" + LocalDate.now().format(DAY) + "/";
		sql.lock("purchase_receipt_serial:" + prefix);
		List<Object[]> latest = sql.rows("SELECT MAX(r.serial) FROM public.purchase_receipt r WHERE r.serial LIKE :prefix", "prefix", prefix + "%");
		int sequence = latest.isEmpty() || latest.get(0)[0] == null ? 1 : Integer.parseInt(str(latest.get(0)[0]).substring(prefix.length())) + 1;
		String receiptId = ID.uuid();
		String userId = sessionUtility.getUserId();
		sql.execute("INSERT INTO public.purchase_receipt (id, serial, po_id, branch_id, invoice_no, note, total, received_by, registered) "
				+ "VALUES (:id, :serial, :po, :branch, :invoice, :note, :total, :user, :now)",
				"id", receiptId, "serial", prefix + String.format("%03d", sequence), "po", poId, "branch", branchId, "invoice", invoice, "note", note,
				"total", total(received), "user", userId, "now", LocalDateTime.now());

		String method = costMethod();
		for (Line line : received) {
			ProductEntity product = line.product();
			BigDecimal stockBefore = dec(sql.rows("SELECT COALESCE(SUM(b.quantity), 0) FROM public.stock_balance b WHERE b.product_id = :p", "p", product.getId()).get(0)[0]);
			BigDecimal costBefore = product.getCostPrice();
			batchService.withBatch(line.batchNo(), line.expiry(), () -> stockService.applyMovement(branchId, product, line.quantity(),
					StockMovementEntity.RECEIVE, "PURCHASE", receiptId, "Pembelian " + str(po[2]), userId, line.unitCost()));
			sql.execute("INSERT INTO public.purchase_receipt_item (receipt_id, product_id, quantity, unit_cost) VALUES (:r, :p, :q, :c)",
					"r", receiptId, "p", product.getId(), "q", line.quantity(), "c", line.unitCost());
			sql.execute("UPDATE public.purchase_order_item SET received_quantity = received_quantity + :q WHERE po_id = :po AND product_id = :p",
					"q", line.quantity(), "po", poId, "p", product.getId());
			BigDecimal newCost = newCost(method, stockBefore, costBefore, line.quantity(), line.unitCost());
			if (newCost != null) {
				priceHistory.record(product.getId(), PriceHistoryService.COST_PRICE, costBefore, newCost, PriceHistoryService.PURCHASE, userId, "Penerimaan " + str(po[2]));
				sql.execute("UPDATE public.product SET cost_price = :c, modificated = :now WHERE id = :id", "c", newCost, "now", LocalDateTime.now(), "id", product.getId());
			}
		}
		boolean complete = dec(sql.rows("SELECT COALESCE(SUM(i.quantity - i.received_quantity), 0) FROM public.purchase_order_item i WHERE i.po_id = :id", "id", poId).get(0)[0]).signum() == 0;
		sql.execute("UPDATE public.purchase_order SET status = :status, modificated = :now WHERE id = :id",
				"status", complete ? "RECEIVED" : "PARTIAL", "now", LocalDateTime.now(), "id", poId);
		return new ResponseModel<PurchaseDTO>().success(detail(poId));
	}

	// AVERAGE: rata-rata tertimbang terhadap seluruh stok sebelum penerimaan (harga pokok produk berlaku lintas cabang).
	// Hasil dibulatkan ke rupiah utuh, sama dengan aturan harga pokok yang diisi manual (kalau tidak, produk tidak bisa diedit lagi).
	private BigDecimal newCost(String method, BigDecimal stockBefore, BigDecimal costBefore, BigDecimal quantity, BigDecimal unitCost) {
		if (method.equals("NONE")) {
			return null;
		}
		if (method.equals("LAST") || costBefore == null || stockBefore.signum() <= 0) {
			return unitCost.setScale(0, RoundingMode.HALF_UP);
		}
		BigDecimal value = stockBefore.multiply(costBefore).add(quantity.multiply(unitCost));
		return value.divide(stockBefore.add(quantity), 0, RoundingMode.HALF_UP);
	}

	private String costMethod() {
		String value = configurationRepository.get(ConfigurationKey.PURCHASE_COST_METHOD);
		String method = value == null ? "AVERAGE" : value.trim().toUpperCase();
		return method.equals("LAST") || method.equals("NONE") ? method : "AVERAGE";
	}

	// ------------------------------------------------------------------ baca

	@Transactional(readOnly = true)
	public ResponseEntity<DataResult<PurchaseDTO>> read(String serial, String status, String supplierId, String branchId, LocalDate from,
			LocalDate to, Integer offset, Integer limit) {
		BranchScope scope = branchScopeService.current();
		StringBuilder where = new StringBuilder("1 = 1");
		Map<String, Object> params = new HashMap<>();
		String wantedBranch = clean(branchId);
		if (wantedBranch != null) {
			if (!scope.contains(wantedBranch)) {
				throw new ApiException("404", "Cabang tidak ditemukan");
			}
			where.append(" AND p.branch_id = :wanted");
			params.put("wanted", wantedBranch);
		} else if (!scope.isGlobal()) {
			where.append(" AND p.branch_id IN (:scope)");
			params.put("scope", scope.getIds());
		}
		if (clean(serial) != null) {
			where.append(" AND LOWER(p.serial) LIKE :serial");
			params.put("serial", "%" + serial.trim().toLowerCase() + "%");
		}
		if (clean(status) != null) {
			where.append(" AND p.status = :status");
			params.put("status", status.trim());
		}
		if (clean(supplierId) != null) {
			where.append(" AND p.supplier_id = :supplier");
			params.put("supplier", supplierId.trim());
		}
		if (from != null) {
			where.append(" AND p.registered >= :from");
			params.put("from", from.atStartOfDay());
		}
		if (to != null) {
			where.append(" AND p.registered < :to");
			params.put("to", to.plusDays(1).atStartOfDay());
		}
		long total = lng(sql.rows("SELECT COUNT(*) FROM public.purchase_order p WHERE " + where, params).get(0)[0]);
		int size = limit == null || limit < 1 ? 10 : Math.min(limit, MAX_PAGE);
		int start = offset == null || offset < 0 ? 0 : offset;
		List<PurchaseDTO> list = headers(where + " ORDER BY p.registered DESC LIMIT " + size + " OFFSET " + start, params);
		return ResponseEntity.ok(new DataResult<>(list, BigInteger.valueOf(total), BigInteger.valueOf(total)));
	}

	@Transactional(readOnly = true)
	public ResponseModel<PurchaseDTO> get(String id) {
		BranchScope scope = branchScopeService.current();
		PurchaseDTO dto = id == null ? null : detail(id.trim());
		if (dto == null || !scope.contains(dto.branchId())) {
			throw new ApiException("404", "Pembelian tidak ditemukan");
		}
		return new ResponseModel<PurchaseDTO>().success(dto);
	}

	// Saran pesan ulang untuk satu cabang: laju jual HISTORY_DAYS hari terakhir, dikurangi stok dan pesanan yang belum datang.
	@Transactional(readOnly = true)
	public ResponseModel<List<SuggestionDTO>> suggestion(String branchId) {
		BranchScope scope = branchScopeService.current();
		BranchEntity branch = branchScopeService.ownBranch(scope, branchId, "Saran hanya untuk cabang Anda sendiri");
		Set<String> visible = branchScopeService.subtree(branch.getId()).getVisibleIds();
		LocalDateTime since = LocalDate.now().minusDays(HISTORY_DAYS).atStartOfDay();
		List<SuggestionDTO> list = new ArrayList<>();
		for (Object[] r : sql.rows("SELECT p.id, p.serial, p.name, p.unit, COALESCE(st.q, 0), COALESCE(inc.q, 0), sold.q, p.cost_price, ls.supplier_id, ls.name "
				+ "FROM (SELECT si.product_id, SUM(si.quantity) AS q FROM public.sale_item si JOIN public.sale s ON s.id = si.sale_id "
				+ "WHERE s.status = 'COMPLETED' AND s.branch_id = :branch AND COALESCE(s.decided_at, s.registered) >= :since GROUP BY si.product_id) sold "
				+ "JOIN public.product p ON p.id = sold.product_id "
				+ "LEFT JOIN (SELECT b.product_id, b.quantity AS q FROM public.stock_balance b WHERE b.branch_id = :branch) st ON st.product_id = p.id "
				+ "LEFT JOIN (SELECT i.product_id, SUM(i.quantity - i.received_quantity) AS q FROM public.purchase_order_item i JOIN public.purchase_order o ON o.id = i.po_id "
				+ "WHERE o.branch_id = :branch AND o.status IN ('ORDERED', 'PARTIAL') GROUP BY i.product_id) inc ON inc.product_id = p.id "
				+ "LEFT JOIN LATERAL (SELECT o.supplier_id, sp.name FROM public.purchase_order_item i JOIN public.purchase_order o ON o.id = i.po_id "
				+ "JOIN public.supplier sp ON sp.id = o.supplier_id WHERE i.product_id = p.id AND o.branch_id = :branch AND o.status <> 'CANCELLED' "
				+ "ORDER BY o.registered DESC LIMIT 1) ls ON true "
				+ "WHERE p.status <> 'DISABLED' AND p.branch_id IN (:visible)",
				SqlHelper.map("branch", branch.getId(), "since", since, "visible", visible))) {
			BigDecimal stock = dec(r[4]);
			BigDecimal incoming = dec(r[5]);
			BigDecimal avgDaily = dec(r[6]).divide(BigDecimal.valueOf(HISTORY_DAYS), 4, RoundingMode.HALF_UP);
			BigDecimal suggested = avgDaily.multiply(BigDecimal.valueOf(SUGGEST_DAYS)).subtract(stock).subtract(incoming)
					.max(BigDecimal.ZERO).setScale(0, RoundingMode.CEILING);
			if (suggested.signum() <= 0) {
				continue;
			}
			Double cover = avgDaily.signum() == 0 ? null : stock.divide(avgDaily, 1, RoundingMode.HALF_UP).doubleValue();
			list.add(new SuggestionDTO(str(r[0]), str(r[1]), str(r[2]), str(r[3]), stock, incoming, avgDaily.setScale(2, RoundingMode.HALF_UP), cover,
					suggested, r[7] == null ? null : dec(r[7]), str(r[8]), str(r[9])));
		}
		list.sort(Comparator.comparing(SuggestionDTO::daysCover, Comparator.nullsLast(Comparator.naturalOrder())));
		return new ResponseModel<List<SuggestionDTO>>().success(list.size() > MAX_SUGGESTIONS ? list.subList(0, MAX_SUGGESTIONS) : list);
	}

	// ------------------------------------------------------------------ susun

	private List<PurchaseDTO> headers(String tail, Map<String, Object> params) {
		List<PurchaseDTO> list = new ArrayList<>();
		for (Object[] r : sql.rows("SELECT p.id, p.serial, p.branch_id, b.name, p.supplier_id, sp.name, p.status, p.expected_date, p.note, p.total, u.username, "
				+ "p.registered, p.ordered_at, p.closed_at, p.close_note, (SELECT COUNT(*) FROM public.purchase_order_item i WHERE i.po_id = p.id) "
				+ "FROM public.purchase_order p JOIN public.branch b ON b.id = p.branch_id JOIN public.supplier sp ON sp.id = p.supplier_id "
				+ "JOIN public.\"user\" u ON u.id = p.created_by WHERE " + tail, params)) {
			list.add(new PurchaseDTO(str(r[0]), str(r[1]), str(r[2]), str(r[3]), str(r[4]), str(r[5]), str(r[6]), date(r[7]), str(r[8]), dec(r[9]),
					str(r[10]), time(r[11]), time(r[12]), time(r[13]), str(r[14]), lng(r[15]), null, null));
		}
		return list;
	}

	private PurchaseDTO detail(String id) {
		List<PurchaseDTO> found = headers("p.id = :id", SqlHelper.map("id", id));
		if (found.isEmpty()) {
			return null;
		}
		PurchaseDTO h = found.get(0);
		List<PurchaseDTO.Item> items = new ArrayList<>();
		for (Object[] r : sql.rows("SELECT i.product_id, pr.serial, pr.name, pr.unit, i.quantity, i.unit_cost, i.received_quantity FROM public.purchase_order_item i "
				+ "JOIN public.product pr ON pr.id = i.product_id WHERE i.po_id = :id ORDER BY pr.name", "id", id)) {
			BigDecimal quantity = dec(r[4]);
			BigDecimal cost = dec(r[5]);
			items.add(new PurchaseDTO.Item(str(r[0]), str(r[1]), str(r[2]), str(r[3]), quantity, cost, dec(r[6]),
					quantity.multiply(cost).setScale(2, RoundingMode.HALF_UP)));
		}
		List<PurchaseDTO.Receipt> receipts = new ArrayList<>();
		for (Object[] r : sql.rows("SELECT g.id, g.serial, g.invoice_no, g.note, g.total, u.username, g.registered FROM public.purchase_receipt g "
				+ "JOIN public.\"user\" u ON u.id = g.received_by WHERE g.po_id = :id ORDER BY g.registered", "id", id)) {
			List<PurchaseDTO.ReceiptItem> lines = new ArrayList<>();
			for (Object[] l : sql.rows("SELECT pr.name, pr.unit, x.quantity, x.unit_cost FROM public.purchase_receipt_item x JOIN public.product pr ON pr.id = x.product_id "
					+ "WHERE x.receipt_id = :r ORDER BY pr.name", "r", str(r[0]))) {
				lines.add(new PurchaseDTO.ReceiptItem(str(l[0]), str(l[1]), dec(l[2]), dec(l[3])));
			}
			receipts.add(new PurchaseDTO.Receipt(str(r[1]), str(r[2]), str(r[3]), dec(r[4]), str(r[5]), time(r[6]), lines));
		}
		return new PurchaseDTO(h.id(), h.serial(), h.branchId(), h.branchName(), h.supplierId(), h.supplierName(), h.status(), h.expectedDate(), h.note(),
				h.total(), h.createdByName(), h.registered(), h.orderedAt(), h.closedAt(), h.closeNote(), h.lineCount(), items, receipts);
	}

	// ------------------------------------------------------------------ validasi

	// Mengunci pesanan di cabang yang boleh diakses; mengembalikan [branch_id, status, serial].
	private Object[] lockOwn(String id) {
		BranchScope scope = branchScopeService.current();
		List<Object[]> rows = id == null ? List.of()
				: sql.rows("SELECT p.branch_id, p.status, p.serial FROM public.purchase_order p WHERE p.id = :id FOR UPDATE", "id", id.trim());
		if (rows.isEmpty() || !scope.contains(str(rows.get(0)[0]))) {
			throw new ApiException("404", "Pembelian tidak ditemukan");
		}
		return rows.get(0);
	}

	private void markOrdered(String id) {
		LocalDateTime now = LocalDateTime.now();
		sql.execute("UPDATE public.purchase_order SET status = 'ORDERED', ordered_at = :now, modificated = :now WHERE id = :id", "id", id, "now", now);
	}

	private List<Line> lines(String branchId, List<PurchaseRequestDTO.Item> items, boolean requireAny) {
		if (items == null || items.isEmpty()) {
			if (requireAny) {
				throw new ApiException("400", "Pesanan harus memuat minimal satu barang");
			}
			return List.of();
		}
		if (items.size() > MAX_LINES) {
			throw new ApiException("400", "Pesanan tidak boleh lebih dari " + MAX_LINES + " barang");
		}
		Set<String> seen = new HashSet<>();
		List<Line> lines = new ArrayList<>();
		for (PurchaseRequestDTO.Item item : items) {
			if (item == null || item.productId() == null || !seen.add(item.productId().trim())) {
				throw new ApiException("400", "Produk tidak boleh ganda atau kosong");
			}
			ProductEntity product = stockService.visibleProduct(branchId, item.productId());
			stockService.assertActive(product);
			BigDecimal quantity = stockService.toBase(product, item.quantity(), item.unit(), false);
			lines.add(new Line(product, quantity, baseCost(item.unitCost(), item.quantity(), quantity), null, null));
		}
		return lines;
	}

	// Harga yang diinput per satuan input diubah menjadi per satuan dasar (harga x jumlah input / jumlah dasar).
	private BigDecimal baseCost(BigDecimal inputCost, BigDecimal inputQuantity, BigDecimal baseQuantity) {
		if (inputCost == null || inputCost.signum() < 0 || inputCost.compareTo(MAX_COST) > 0) {
			throw new ApiException("400", "Harga beli tidak valid");
		}
		return inputCost.multiply(inputQuantity).divide(baseQuantity, 2, RoundingMode.HALF_UP);
	}

	private BigDecimal total(List<Line> lines) {
		return lines.stream().map(l -> l.quantity().multiply(l.unitCost())).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
	}

	private void insertLines(String poId, List<Line> lines) {
		for (Line line : lines) {
			sql.execute("INSERT INTO public.purchase_order_item (po_id, product_id, quantity, unit_cost, received_quantity) VALUES (:po, :p, :q, :c, 0)",
					"po", poId, "p", line.product().getId(), "q", line.quantity(), "c", line.unitCost());
		}
	}

	private String note(String value) {
		String note = clean(value);
		if (note != null && note.length() > MAX_NOTE) {
			throw new ApiException("400", "Catatan tidak boleh lebih dari " + MAX_NOTE + " karakter");
		}
		return note;
	}
}
