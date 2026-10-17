package com.saasno.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.repository.BranchRepository;
import com.saasno.repository.OnlineOrderRepository;
import com.saasno.repository.OnlineOrderRepository.Item;
import com.saasno.repository.OnlineOrderRepository.Row;
import com.saasno.repository.StockRepository;
import com.saasno.repository.UserRepository;
import com.saasno.utility.BranchScope;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.dto.order.OrderDTO;
import com.saasno.utility.dto.product.ProductUnitDTO;
import com.saasno.utility.dto.sale.SaleDTO;
import com.saasno.utility.dto.sale.SaleRequestDTO;
import com.saasno.utility.dto.sale.SaleRequestItemDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.logger.Log;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataResult;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;

// Pesanan online dari sisi staf cabang. Alur: PENDING -> APPROVED (stok dikunci) -> READY -> COMPLETED (transaksi penjualan
// dibuat, stok berkurang). PENDING, APPROVED, dan READY bisa ditolak atau dibatalkan dengan alasan yang dibaca pembeli.
// Stok dikunci lunak: POS biasa tidak memperhitungkannya, jadi bila stok habis dijual di kasir, penyelesaian ditolak 409.
@Service
public class OnlineOrderService {

	private static final int MAX_NOTE = 300;
	private static final int MAX_PAGE = 100;
	private static final Set<String> STATUSES = Set.of("PENDING", "APPROVED", "READY", "COMPLETED", "REJECTED", "CANCELLED");

	@Autowired
	private OnlineOrderRepository orderRepository;

	@Autowired
	private StockRepository stockRepository;

	@Autowired
	private BranchRepository branchRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private BranchScopeService branchScopeService;

	@Autowired
	private ProductUnitService productUnitService;

	@Autowired
	private SaleCalculator saleCalculator;

	@Autowired
	private SaleService saleService;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private SessionUtility sessionUtility;

	// ---------------------------------------------------------------- baca

	@Transactional(readOnly = true)
	public ResponseEntity<DataResult<OrderDTO>> read(HttpServletRequest request) {
		try {
			return new ResponseEntity<>(find(request::getParameter, branchScopeService.current()), HttpStatus.OK);
		} catch (ApiException e) {
			throw e;
		} catch (Exception e) {
			Log.error(e.getMessage(), e);
			return new ResponseEntity<>(new DataResult<OrderDTO>(BigInteger.ZERO, BigInteger.ZERO), HttpStatus.OK);
		}
	}

	// Isi read untuk satu set parameter dan cakupan cabang (dipakai juga oleh pengunduhan).
	@Transactional(readOnly = true)
	public DataResult<OrderDTO> find(java.util.function.UnaryOperator<String> request, BranchScope scope) {
		{
			String status = clean(request.apply("status"));
			if (status != null && !STATUSES.contains(status)) {
				throw new ApiException("400", "Status tidak valid");
			}
			String serial = clean(request.apply("serial"));
			String name = clean(request.apply("name"));
			// Nomor HP disimpan dalam format 62...; yang diketik staf boleh berawalan 0.
			if (name != null && name.matches("0\\d*")) {
				name = "62" + name.substring(1);
			}
			int limit = Math.max(1, Math.min(MAX_PAGE, number(request.apply("limit"), 10)));
			int offset = Math.max(0, number(request.apply("offset"), 0));
			Set<String> branches = scope.isGlobal() ? null : scope.getIds();
			List<Row> rows = orderRepository.list(branches, status, serial, name, offset, limit);
			BigInteger total = BigInteger.valueOf(orderRepository.count(branches, status, serial, name));
			return new DataResult<>(dtos(rows), total, total);
		}
	}

	// ---------------------------------------------------------------- putuskan dan proses

	// Menyetujui = stok dikunci untuk pesanan ini. Baris stok dikunci dalam urutan produk tetap (mencegah deadlock), lalu dicek:
	// stok - yang sudah dikunci pesanan lain harus cukup. Total pasti dihitung mesin penjualan (promo, pajak) dan ditampilkan ke pembeli.
	@Transactional
	public ResponseModel<OrderDTO> approve(String id, String note) {
		Row row = lockForStaff(id, OnlineOrderRepository.PENDING);
		String text = optional(note);
		List<Item> items = orderRepository.items(List.of(row.id()));
		for (Item item : items.stream().sorted(java.util.Comparator.comparing(Item::productId)).toList()) {
			BigDecimal stock = stockRepository.lockQuantity(row.branchId(), item.productId());
			BigDecimal free = stock.subtract(orderRepository.reserved(row.branchId(), item.productId(), row.id()));
			if (free.compareTo(item.quantity()) < 0) {
				throw new ApiException("409", "Stok " + item.productName() + " tidak mencukupi (tersedia " + free.stripTrailingZeros().toPlainString() + ")");
			}
		}
		BigDecimal confirmed = saleCalculator.calculate(row.branchId(), saleRequest(row, items, null)).total;
		orderRepository.updateStatus(row.id(), OnlineOrderRepository.APPROVED, sessionUtility.getUserId(), text, confirmed, null);
		orderRepository.insertEvent(row.id(), OnlineOrderRepository.APPROVED, text, sessionUtility.getUserId());
		return reload(row.id());
	}

	@Transactional
	public ResponseModel<OrderDTO> reject(String id, String note) {
		Row row = lockForStaff(id, OnlineOrderRepository.PENDING);
		String text = required(note, "Alasan");
		orderRepository.updateStatus(row.id(), OnlineOrderRepository.REJECTED, sessionUtility.getUserId(), text, null, null);
		orderRepository.insertEvent(row.id(), OnlineOrderRepository.REJECTED, text, sessionUtility.getUserId());
		return reload(row.id());
	}

	// Siap diambil (pesanan ambil di toko) atau sedang diantar (pesanan antar); catatan boleh berisi jam atau info lain.
	@Transactional
	public ResponseModel<OrderDTO> ready(String id, String note) {
		Row row = lockForStaff(id, OnlineOrderRepository.APPROVED);
		String text = optional(note);
		orderRepository.updateStatus(row.id(), OnlineOrderRepository.READY, null, null, null, null);
		orderRepository.insertEvent(row.id(), OnlineOrderRepository.READY, text, sessionUtility.getUserId());
		return reload(row.id());
	}

	// Menyelesaikan (hanya dari READY) = pembayaran diterima: transaksi penjualan dibuat dengan harga saat ini (stok berkurang, masuk laporan kas).
	// paid kosong = pas sebesar total yang disetujui (mis. transfer). Hanya staf cabang itu sendiri (aturan transaksi penjualan).
	@Transactional
	public ResponseModel<OrderDTO> complete(String id, BigDecimal paid) {
		// Hanya setelah ditandai siap (diambil/diantar): menyelesaikan = barang sudah di tangan pembeli dan pembayaran diterima.
		Row row = lockForStaff(id, OnlineOrderRepository.READY);
		List<Item> items = orderRepository.items(List.of(row.id()));
		BigDecimal amount = paid != null ? paid : row.confirmedTotal();
		SaleDTO sale = saleService.create(saleRequest(row, items, amount, "online-" + row.id()), false).getData();
		if (sale == null || !"COMPLETED".equals(sale.getStatus())) {
			throw new ApiException("409", "Transaksi penjualan tidak dapat diselesaikan");
		}
		orderRepository.updateStatus(row.id(), OnlineOrderRepository.COMPLETED, null, null, null, sale.getId());
		orderRepository.insertEvent(row.id(), OnlineOrderRepository.COMPLETED, null, sessionUtility.getUserId());
		return reload(row.id());
	}

	// Pembatalan oleh staf (stok yang dikunci dilepas); alasan dibaca pembeli.
	@Transactional
	public ResponseModel<OrderDTO> cancel(String id, String note) {
		Row row = lockForStaff(id, OnlineOrderRepository.PENDING, OnlineOrderRepository.APPROVED, OnlineOrderRepository.READY);
		String text = required(note, "Alasan");
		orderRepository.updateStatus(row.id(), OnlineOrderRepository.CANCELLED, sessionUtility.getUserId(), text, null, null);
		orderRepository.insertEvent(row.id(), OnlineOrderRepository.CANCELLED, text, sessionUtility.getUserId());
		return reload(row.id());
	}

	// ---------------------------------------------------------------- bantu

	// Pesanan harus di cabang dalam cakupan user (selain itu dijawab 404) dan berstatus yang diizinkan. Baris dikunci supaya dua
	// tindakan bersamaan atas pesanan yang sama tidak keduanya berhasil.
	private Row lockForStaff(String id, String... allowed) {
		BranchScope scope = branchScopeService.current();
		Row row = id == null ? null : orderRepository.find(id.trim(), true).orElse(null);
		if (row == null || !scope.contains(row.branchId())) {
			throw new ApiException("404", "Pesanan tidak ditemukan");
		}
		if (!List.of(allowed).contains(row.status())) {
			throw new ApiException("409", "Pesanan sudah berstatus " + row.status());
		}
		return row;
	}

	private SaleRequestDTO saleRequest(Row row, List<Item> items, BigDecimal paid) {
		return saleRequest(row, items, paid, null);
	}

	// Jumlah pesanan sudah dalam satuan dasar, jadi dikirim tanpa satuan.
	private SaleRequestDTO saleRequest(Row row, List<Item> items, BigDecimal paid, String key) {
		SaleRequestDTO request = new SaleRequestDTO();
		request.setBranchId(row.branchId());
		request.setPaid(paid);
		request.setRequestKey(key);
		List<SaleRequestItemDTO> list = new ArrayList<>();
		for (Item item : items) {
			SaleRequestItemDTO line = new SaleRequestItemDTO();
			line.setProductId(item.productId());
			line.setQuantity(item.quantity());
			list.add(line);
		}
		request.setItems(list);
		return request;
	}

	private ResponseModel<OrderDTO> reload(String id) {
		Row row = orderRepository.find(id, false).orElseThrow(() -> new ApiException("404", "Pesanan tidak ditemukan"));
		return new ResponseModel<OrderDTO>().success(dtos(List.of(row)).get(0));
	}

	@SuppressWarnings("unchecked")
	private List<OrderDTO> dtos(List<Row> rows) {
		if (rows.isEmpty()) {
			return List.of();
		}
		Set<String> ids = rows.stream().map(Row::id).collect(Collectors.toSet());
		Map<String, List<Item>> items = orderRepository.items(ids).stream().collect(Collectors.groupingBy(Item::orderId));
		Map<String, String> branchNames = branchScopeService.names(rows.stream().map(Row::branchId).collect(Collectors.toSet()));
		Set<String> userIds = rows.stream().map(Row::decidedBy).filter(u -> u != null).collect(Collectors.toSet());
		Map<String, String> usernames = userIds.isEmpty() ? Map.of() : userRepository.findUsernames(userIds);
		Map<String, String> unitLabels = new HashMap<>();
		for (ProductUnitDTO unit : productUnitService.units()) {
			unitLabels.put(unit.getValue(), unit.getLabel());
		}
		Set<String> saleIds = rows.stream().map(Row::saleId).filter(s -> s != null).collect(Collectors.toSet());
		Map<String, String> saleSerials = new HashMap<>();
		if (!saleIds.isEmpty()) {
			for (Object[] r : (List<Object[]>) entityManager.createNativeQuery("SELECT id, serial FROM public.sale WHERE id IN (:ids)")
					.setParameter("ids", saleIds).getResultList()) {
				saleSerials.put((String) r[0], (String) r[1]);
			}
		}
		List<OrderDTO> list = new ArrayList<>();
		for (Row row : rows) {
			List<OrderDTO.Item> orderItems = items.getOrDefault(row.id(), List.of()).stream()
					.map(i -> new OrderDTO.Item(i.productId(), i.productName(), i.unit(), unitLabels.getOrDefault(i.unit(), i.unit()), i.quantity(), i.unitPrice()))
					.toList();
			List<OrderDTO.Event> events = orderRepository.events(row.id()).stream().map(e -> new OrderDTO.Event(e.status(), e.note(), e.at())).toList();
			list.add(new OrderDTO(row.id(), row.serial(), row.branchId(), branchNames.get(row.branchId()), row.status(), row.fulfillment(),
					row.paymentMethod(), row.customerName(), row.customerPhone(), row.address(), row.note(), row.estimateTotal(), row.confirmedTotal(),
					row.saleId(), row.saleId() == null ? null : saleSerials.get(row.saleId()),
					row.decidedBy() == null ? null : usernames.get(row.decidedBy()), row.decidedAt(), row.decisionNote(), row.registered(),
					orderItems, events));
		}
		return list;
	}

	private String required(String value, String label) {
		String text = clean(value);
		if (text == null) {
			throw new ApiException("400", label + " tidak boleh kosong");
		}
		return limit(text);
	}

	private String optional(String value) {
		String text = clean(value);
		return text == null ? null : limit(text);
	}

	private String limit(String text) {
		if (text.length() > MAX_NOTE) {
			throw new ApiException("400", "Catatan tidak boleh lebih dari " + MAX_NOTE + " karakter");
		}
		return text;
	}

	private String clean(String value) {
		if (value == null) {
			return null;
		}
		String text = value.trim();
		return text.isEmpty() ? null : text;
	}

	private int number(String value, int fallback) {
		try {
			return value == null ? fallback : Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}


}
