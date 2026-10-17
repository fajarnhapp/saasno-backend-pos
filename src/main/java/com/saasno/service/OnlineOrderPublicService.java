package com.saasno.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.repository.BranchRepository;
import com.saasno.repository.OnlineOrderRepository;
import com.saasno.repository.OnlineOrderRepository.Row;
import com.saasno.repository.StockRepository;
import com.saasno.utility.WhatsappNumber;
import com.saasno.utility.dto.order.PublicOrderDTO;
import com.saasno.utility.dto.order.PublicOrderRequest;
import com.saasno.utility.dto.product.ProductUnitDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.model.ResponseModel;

import jakarta.persistence.EntityManager;

// Sisi pembeli pesanan online: memesan dan melacak tanpa akun. Kunci akses pembeli adalah token acak 160 bit di tautan pelacakan
// (bukan nomor pesanan, yang berurutan dan bisa ditebak). Harga dan stok selalu dihitung server dari katalog; yang dikirim pembeli
// hanya produk, jumlah, dan data kontak. Pembatasan: maksimal 3 pesanan menunggu per nomor HP per cabang, dan 100 per cabang.
@Service
public class OnlineOrderPublicService {

	private static final int MAX_ITEMS = 50;
	private static final int MAX_QUANTITY = 999;
	private static final int MAX_PENDING_PER_PHONE = 3;
	private static final int MAX_PENDING_PER_BRANCH = 100;
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final String BASE32 = "abcdefghijklmnopqrstuvwxyz234567";

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private OnlineOrderRepository orderRepository;

	@Autowired
	private CatalogPublicService catalogPublicService;

	@Autowired
	private BranchRepository branchRepository;

	@Autowired
	private StockRepository stockRepository;

	@Autowired
	private ProductUnitService productUnitService;

	@Autowired
	private NotificationPublisher notificationPublisher;

	@Autowired
	private NotificationRecipients notificationRecipients;

	private record Line(String id, String name, String unit, BigDecimal price, BigDecimal quantity) {
	}

	// ---------------------------------------------------------------- pesan

	@Transactional
	public ResponseModel<PublicOrderDTO> place(String slug, PublicOrderRequest request) {
		CatalogPublicService.Catalog catalog = catalogPublicService.find(slug);
		if (request == null) {
			throw new ApiException("400", "Pesanan tidak valid");
		}
		String name = text(request.name(), 2, 80, "Nama");
		String phone = WhatsappNumber.normalize(request.phone());
		String fulfillment = oneOf(request.fulfillment(), "Cara pengambilan", "PICKUP", "DELIVERY");
		String payment = oneOf(request.paymentMethod(), "Cara pembayaran", "CASH", "TRANSFER");
		String address = "DELIVERY".equals(fulfillment) ? text(request.address(), 10, 300, "Alamat pengantaran") : null;
		String note = optional(request.note(), 300, "Catatan");

		List<PublicOrderRequest.Item> items = request.items() == null ? List.of() : request.items();
		if (items.isEmpty() || items.size() > MAX_ITEMS) {
			throw new ApiException("400", "Pesanan harus berisi 1 sampai " + MAX_ITEMS + " barang");
		}
		if (orderRepository.pendingByPhone(catalog.branchId(), phone) >= MAX_PENDING_PER_PHONE) {
			throw new ApiException("429", "Anda masih punya " + MAX_PENDING_PER_PHONE + " pesanan yang belum diproses toko");
		}
		if (orderRepository.pendingByBranch(catalog.branchId()) >= MAX_PENDING_PER_BRANCH) {
			throw new ApiException("429", "Toko sedang menerima terlalu banyak pesanan, coba lagi nanti");
		}

		Set<String> seen = new HashSet<>();
		List<Line> lines = new ArrayList<>();
		BigDecimal estimate = BigDecimal.ZERO;
		for (PublicOrderRequest.Item item : items) {
			String productId = item == null || item.productId() == null ? "" : item.productId().trim();
			BigDecimal quantity = item == null ? null : item.quantity();
			if (productId.isEmpty() || !seen.add(productId)) {
				throw new ApiException("400", "Produk tidak valid atau ganda");
			}
			if (quantity == null || quantity.signum() <= 0 || quantity.stripTrailingZeros().scale() > 0
					|| quantity.compareTo(BigDecimal.valueOf(MAX_QUANTITY)) > 0) {
				throw new ApiException("400", "Jumlah harus bilangan bulat 1 sampai " + MAX_QUANTITY);
			}
			Line line = visibleProduct(productId, catalog.branchId(), quantity);
			BigDecimal free = stockRepository.quantity(catalog.branchId(), productId)
					.subtract(orderRepository.reserved(catalog.branchId(), productId, null));
			if (free.compareTo(quantity) < 0) {
				throw new ApiException("409", "Stok " + line.name() + " tidak mencukupi");
			}
			lines.add(line);
			estimate = estimate.add(line.price().multiply(quantity));
		}
		estimate = estimate.setScale(2, RoundingMode.HALF_UP);

		String token = token();
		String serial = orderRepository.nextSerial();
		String id = orderRepository.insert(serial, token, catalog.branchId(), fulfillment, payment, name, phone, address, note, estimate);
		for (Line line : lines) {
			orderRepository.insertItem(id, line.id(), line.name(), line.unit(), line.quantity(), line.price());
		}
		orderRepository.insertEvent(id, OnlineOrderRepository.PENDING, null, null);

		notificationPublisher.notify(notificationRecipients.withApi("PUT_API_ORDER_APPROVE", catalog.branchId()), "ORDER_NEW",
				"Pesanan online baru",
				serial + " dari " + name + " (" + lines.size() + " jenis barang, " + notificationRecipients.rupiah(estimate) + "), "
						+ ("DELIVERY".equals(fulfillment) ? "diantar" : "diambil di toko") + ".",
				"/transaction/order");
		return track(token);
	}

	// ---------------------------------------------------------------- lacak dan batalkan

	@Transactional(readOnly = true)
	public ResponseModel<PublicOrderDTO> track(String token) {
		Row row = find(token, false);
		return new ResponseModel<PublicOrderDTO>().success(dto(row));
	}

	// Pembeli hanya bisa membatalkan selama toko belum menyetujui (setelah itu stok sudah dikunci untuknya: hubungi toko).
	@Transactional
	public ResponseModel<PublicOrderDTO> cancel(String token) {
		Row row = find(token, true);
		if (!OnlineOrderRepository.PENDING.equals(row.status())) {
			throw new ApiException("409", "Pesanan sudah diproses toko, hubungi toko untuk membatalkannya");
		}
		orderRepository.updateStatus(row.id(), OnlineOrderRepository.CANCELLED, null, null, null, null);
		orderRepository.insertEvent(row.id(), OnlineOrderRepository.CANCELLED, "Dibatalkan oleh pembeli", null);
		notificationPublisher.notify(notificationRecipients.withApi("PUT_API_ORDER_APPROVE", row.branchId()), "ORDER_CANCELLED",
				"Pesanan online dibatalkan pembeli", row.serial() + " dari " + row.customerName() + " dibatalkan oleh pembeli.", "/transaction/order");
		return track(token);
	}

	// ---------------------------------------------------------------- daftar pesanan di perangkat pembeli

	private static final int MAX_STATUS_TOKENS = 20;

	// Status beberapa pesanan sekaligus untuk daftar "Pesanan saya": browser mengirim token-token yang disimpannya. Token dipakai sebagai
	// kunci akses seperti pada halaman pelacakan, jadi tidak ada data pribadi yang keluar; token salah bentuk atau tidak dikenal dilewati.
	@Transactional(readOnly = true)
	public ResponseModel<List<com.saasno.utility.dto.order.PublicOrderSummaryDTO>> statuses(List<String> tokens) {
		List<String> valid = tokens == null ? List.of() : tokens.stream().filter(t -> t != null && t.matches("[a-z2-7]{32}")).distinct().limit(MAX_STATUS_TOKENS).toList();
		List<com.saasno.utility.dto.order.PublicOrderSummaryDTO> list = new ArrayList<>();
		for (Object[] r : orderRepository.summaries(valid)) {
			list.add(new com.saasno.utility.dto.order.PublicOrderSummaryDTO((String) r[0], (String) r[1], (String) r[2], (String) r[3], (String) r[4],
					(BigDecimal) r[5], (Boolean) r[6], ((Number) r[7]).intValue(), time(r[8]), time(r[9])));
		}
		return new ResponseModel<List<com.saasno.utility.dto.order.PublicOrderSummaryDTO>>().success(list);
	}

	private static java.time.LocalDateTime time(Object value) {
		return value instanceof java.sql.Timestamp t ? t.toLocalDateTime() : (java.time.LocalDateTime) value;
	}

	// ---------------------------------------------------------------- cari dengan nomor pesanan

	// Nomor pesanan berurutan dan mudah ditebak, jadi tidak pernah cukup sendirian: harus dipasangkan dengan nomor HP pemesan. Jawaban
	// sama untuk "nomor salah" dan "HP salah" supaya tidak bisa dipakai menebak. Kegagalan per nomor pesanan dibatasi (10 per 15 menit)
	// supaya HP tidak bisa ditebak dengan mencoba satu per satu; pembeli yang terkunci masih bisa memakai tautan pelacakannya.
	@Transactional(readOnly = true)
	public ResponseModel<java.util.Map<String, String>> lookup(String serial, String phone) {
		String number = serial == null ? "" : serial.trim().toUpperCase();
		if (!number.matches("ONL-\\d{6}-\\d{4}")) {
			throw new ApiException("404", NOT_FOUND_LOOKUP);
		}
		String normalized = WhatsappNumber.normalize(phone);
		if (blocked(number)) {
			throw new ApiException("429", "Terlalu banyak percobaan untuk nomor pesanan ini, coba lagi beberapa menit lagi");
		}
		Optional<String> token = orderRepository.tokenBySerialAndPhone(number, normalized);
		if (token.isEmpty()) {
			fail(number);
			throw new ApiException("404", NOT_FOUND_LOOKUP);
		}
		return new ResponseModel<java.util.Map<String, String>>().success(java.util.Map.of("token", token.get()));
	}

	private static final String NOT_FOUND_LOOKUP = "Pesanan tidak ditemukan. Periksa nomor pesanan dan nomor HP yang dipakai saat memesan.";
	private static final int MAX_FAILURES = 10;
	private static final long FAILURE_WINDOW_MS = 15 * 60 * 1000L;
	private final java.util.concurrent.ConcurrentHashMap<String, long[]> failures = new java.util.concurrent.ConcurrentHashMap<>();

	private boolean blocked(String serial) {
		long[] entry = failures.get(serial);
		return entry != null && System.currentTimeMillis() < entry[1] && entry[0] >= MAX_FAILURES;
	}

	private void fail(String serial) {
		long now = System.currentTimeMillis();
		failures.merge(serial, new long[] { 1, now + FAILURE_WINDOW_MS }, (old, fresh) -> now >= old[1] ? fresh : new long[] { old[0] + 1, old[1] });
		if (failures.size() > 10_000) {
			failures.entrySet().removeIf(e -> now >= e.getValue()[1]);
		}
	}

	// ---------------------------------------------------------------- bantu

	// Token yang salah bentuk dijawab sama dengan yang tidak ada, tanpa menyentuh database.
	private Row find(String token, boolean lock) {
		if (token == null || !token.matches("[a-z2-7]{32}")) {
			throw new ApiException("404", "Pesanan tidak ditemukan");
		}
		Optional<Row> row = orderRepository.findByToken(token, lock);
		return row.orElseThrow(() -> new ApiException("404", "Pesanan tidak ditemukan"));
	}

	private PublicOrderDTO dto(Row row) {
		Map<String, String> unitLabels = new HashMap<>();
		for (ProductUnitDTO unit : productUnitService.units()) {
			unitLabels.put(unit.getValue(), unit.getLabel());
		}
		List<PublicOrderDTO.Item> items = orderRepository.items(List.of(row.id())).stream()
				.map(i -> new PublicOrderDTO.Item(i.productName(), unitLabels.getOrDefault(i.unit(), i.unit()), i.quantity(), i.unitPrice())).toList();
		List<PublicOrderDTO.Event> events = orderRepository.events(row.id()).stream()
				.map(e -> new PublicOrderDTO.Event(e.status(), e.note(), e.at())).toList();
		BigDecimal total = row.confirmedTotal() != null ? row.confirmedTotal() : row.estimateTotal();
		if (row.saleId() != null) {
			Object sold = entityManager.createNativeQuery("SELECT total FROM public.sale WHERE id = :id").setParameter("id", row.saleId()).getSingleResult();
			total = (BigDecimal) sold;
		}
		Object[] branch = (Object[]) entityManager.createNativeQuery("SELECT b.name, bc.whatsapp FROM public.branch b "
				+ "LEFT JOIN public.branch_catalog bc ON bc.branch_id = b.id WHERE b.id = :id").setParameter("id", row.branchId()).getSingleResult();
		boolean finalTotal = row.confirmedTotal() != null || row.saleId() != null;
		return new PublicOrderDTO(row.token(), row.serial(), (String) branch[0], (String) branch[1], row.status(), row.fulfillment(),
				row.paymentMethod(), row.customerName(), mask(row.customerPhone()), row.address(), row.note(), total, finalTotal,
				OnlineOrderRepository.PENDING.equals(row.status()), row.registered(), items, events);
	}

	@SuppressWarnings("unchecked")
	private Line visibleProduct(String productId, String branchId, BigDecimal quantity) {
		// Sama dengan isi katalog: aktif, ada stoknya di cabang ini, dan tidak disembunyikan.
		List<Object[]> rows = entityManager.createNativeQuery("SELECT p.id, p.name, p.unit, p.price FROM public.stock_balance sb "
				+ "JOIN public.product p ON p.id = sb.product_id LEFT JOIN public.product_catalog c ON c.product_id = p.id "
				+ "WHERE sb.branch_id = :branch AND sb.quantity > 0 AND p.id = :id AND p.status = 'ACTIVE' AND NOT COALESCE(c.hidden, false)")
				.setParameter("id", productId).setParameter("branch", branchId).getResultList();
		if (rows.isEmpty()) {
			throw new ApiException("409", "Ada produk yang sudah tidak tersedia di katalog. Muat ulang halaman lalu coba lagi.");
		}
		Object[] r = rows.get(0);
		return new Line((String) r[0], (String) r[1], (String) r[2], (BigDecimal) r[3], quantity);
	}

	// 62812****789: cukup untuk dikenali pemiliknya, tidak cukup untuk disalahgunakan bila tautan bocor.
	private String mask(String phone) {
		return phone.length() <= 7 ? phone : phone.substring(0, 4) + "*".repeat(phone.length() - 7) + phone.substring(phone.length() - 3);
	}

	private String token() {
		byte[] bytes = new byte[20];
		RANDOM.nextBytes(bytes);
		StringBuilder out = new StringBuilder(32);
		int buffer = 0;
		int bits = 0;
		for (byte b : bytes) {
			buffer = (buffer << 8) | (b & 0xff);
			bits += 8;
			while (bits >= 5) {
				out.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
				bits -= 5;
			}
		}
		return out.toString();
	}

	private String text(String value, int min, int max, String label) {
		String text = clean(value);
		if (text == null || text.length() < min) {
			throw new ApiException("400", label + " minimal " + min + " karakter");
		}
		if (text.length() > max) {
			throw new ApiException("400", label + " maksimal " + max + " karakter");
		}
		return text;
	}

	private String optional(String value, int max, String label) {
		String text = clean(value);
		if (text != null && text.length() > max) {
			throw new ApiException("400", label + " maksimal " + max + " karakter");
		}
		return text;
	}

	// Spasi berlebih dirapikan dan karakter kontrol dibuang (nama dan catatan tampil di layar staf dan pesan WhatsApp).
	private String clean(String value) {
		if (value == null) {
			return null;
		}
		String text = value.replaceAll("[\\p{Cntrl}&&[^\\n]]", "").replaceAll("[ \\t]+", " ").trim();
		return text.isEmpty() ? null : text;
	}

	private String oneOf(String value, String label, String... allowed) {
		String text = value == null ? "" : value.trim().toUpperCase();
		for (String option : allowed) {
			if (option.equals(text)) {
				return option;
			}
		}
		throw new ApiException("400", label + " tidak valid");
	}

}
