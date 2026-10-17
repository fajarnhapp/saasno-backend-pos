package com.saasno.repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.utility.generator.ID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

// Akses data pesanan online (SQL langsung: tabel ini hanya disentuh lewat layanan pesanan). Semua pembacaan yang diikuti
// perubahan status memakai lock baris (FOR UPDATE) supaya dua keputusan bersamaan tidak keduanya berhasil.
@Repository
public class OnlineOrderRepository {

	public static final String PENDING = "PENDING";
	public static final String APPROVED = "APPROVED";
	public static final String READY = "READY";
	public static final String COMPLETED = "COMPLETED";
	public static final String REJECTED = "REJECTED";
	public static final String CANCELLED = "CANCELLED";

	private static final DateTimeFormatter SERIAL_DAY = DateTimeFormatter.ofPattern("yyMMdd");
	private static final String COLUMNS = "o.id, o.serial, o.token, o.branch_id, o.status, o.fulfillment, o.payment_method, o.customer_name, "
			+ "o.customer_phone, o.address, o.note, o.estimate_total, o.confirmed_total, o.sale_id, o.decided_by, o.decided_at, "
			+ "o.decision_note, o.registered";

	public record Row(String id, String serial, String token, String branchId, String status, String fulfillment,
			String paymentMethod, String customerName, String customerPhone, String address, String note, BigDecimal estimateTotal,
			BigDecimal confirmedTotal, String saleId, String decidedBy, LocalDateTime decidedAt, String decisionNote,
			LocalDateTime registered) {
	}

	public record Item(String orderId, String productId, String productName, String unit, BigDecimal quantity, BigDecimal unitPrice) {
	}

	public record Event(String status, String note, LocalDateTime at) {
	}

	@Autowired
	private EntityManager entityManager;

	// ---------------------------------------------------------------- baca

	public Optional<Row> find(String id, boolean lock) {
		return one("WHERE o.id = :v", id, lock);
	}

	public Optional<Row> findByToken(String token, boolean lock) {
		return one("WHERE o.token = :v", token, lock);
	}

	// Token pelacakan untuk pasangan nomor pesanan dan nomor HP (sudah dinormalisasi) yang cocok; kosong bila tidak ada.
	@SuppressWarnings("unchecked")
	public Optional<String> tokenBySerialAndPhone(String serial, String phone) {
		List<String> rows = entityManager.createNativeQuery("SELECT token FROM public.online_order WHERE serial = :serial AND customer_phone = :phone")
				.setParameter("serial", serial).setParameter("phone", phone).getResultList();
		return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
	}

	// Ringkasan beberapa pesanan sekaligus lewat token (urutan hasil mengikuti pesanan terbaru dulu). Token yang tidak ada dilewati.
	@SuppressWarnings("unchecked")
	public List<Object[]> summaries(Collection<String> tokens) {
		if (tokens.isEmpty()) {
			return List.of();
		}
		return entityManager.createNativeQuery("SELECT o.token, o.serial, b.name, o.status, o.fulfillment, "
				+ "COALESCE(s.total, o.confirmed_total, o.estimate_total), (o.confirmed_total IS NOT NULL OR o.sale_id IS NOT NULL), "
				+ "(SELECT COUNT(*) FROM public.online_order_item i WHERE i.order_id = o.id), o.registered, o.modificated "
				+ "FROM public.online_order o JOIN public.branch b ON b.id = o.branch_id LEFT JOIN public.sale s ON s.id = o.sale_id "
				+ "WHERE o.token IN (:tokens) ORDER BY o.registered DESC").setParameter("tokens", tokens).getResultList();
	}

	@SuppressWarnings("unchecked")
	private Optional<Row> one(String where, String value, boolean lock) {
		List<Object[]> rows = entityManager.createNativeQuery("SELECT " + COLUMNS + " FROM public.online_order o " + where
				+ (lock ? " FOR UPDATE" : "")).setParameter("v", value).getResultList();
		return rows.isEmpty() ? Optional.empty() : Optional.of(row(rows.get(0)));
	}

	// Daftar untuk staf: cabang dalam cakupan (branchIds null = semua), filter opsional, terbaru dulu.
	@SuppressWarnings("unchecked")
	public List<Row> list(Collection<String> branchIds, String status, String serial, String name, int offset, int limit) {
		Query query = entityManager.createNativeQuery("SELECT " + COLUMNS + " FROM public.online_order o " + filter(branchIds, status, serial, name)
				+ " ORDER BY o.registered DESC, o.id");
		bind(query, branchIds, status, serial, name);
		query.setFirstResult(offset).setMaxResults(limit);
		return ((List<Object[]>) query.getResultList()).stream().map(this::row).toList();
	}

	public long count(Collection<String> branchIds, String status, String serial, String name) {
		Query query = entityManager.createNativeQuery("SELECT COUNT(*) FROM public.online_order o " + filter(branchIds, status, serial, name));
		bind(query, branchIds, status, serial, name);
		return ((Number) query.getSingleResult()).longValue();
	}

	private String filter(Collection<String> branchIds, String status, String serial, String name) {
		return "WHERE 1 = 1" + (branchIds == null ? "" : " AND o.branch_id IN (:branches)") + (status == null ? "" : " AND o.status = :status")
				+ (serial == null ? "" : " AND LOWER(o.serial) LIKE :serial ESCAPE '\\'")
				+ (name == null ? "" : " AND (LOWER(o.customer_name) LIKE :name ESCAPE '\\' OR o.customer_phone LIKE :name ESCAPE '\\')");
	}

	private void bind(Query query, Collection<String> branchIds, String status, String serial, String name) {
		if (branchIds != null) {
			query.setParameter("branches", branchIds);
		}
		if (status != null) {
			query.setParameter("status", status);
		}
		if (serial != null) {
			query.setParameter("serial", like(serial));
		}
		if (name != null) {
			query.setParameter("name", like(name));
		}
	}

	@SuppressWarnings("unchecked")
	public List<Item> items(Collection<String> orderIds) {
		if (orderIds.isEmpty()) {
			return List.of();
		}
		List<Object[]> rows = entityManager.createNativeQuery("SELECT order_id, product_id, product_name, unit, quantity, unit_price "
				+ "FROM public.online_order_item WHERE order_id IN (:ids) ORDER BY product_name, product_id").setParameter("ids", orderIds).getResultList();
		return rows.stream().map(r -> new Item((String) r[0], (String) r[1], (String) r[2], (String) r[3], (BigDecimal) r[4], (BigDecimal) r[5])).toList();
	}

	@SuppressWarnings("unchecked")
	public List<Event> events(String orderId) {
		List<Object[]> rows = entityManager.createNativeQuery("SELECT status, note, at FROM public.online_order_event WHERE order_id = :id ORDER BY at, id")
				.setParameter("id", orderId).getResultList();
		return rows.stream().map(r -> new Event((String) r[0], (String) r[1], time(r[2]))).toList();
	}

	// Jumlah yang sedang dikunci untuk produk ini di cabang ini oleh pesanan yang sudah disetujui tetapi belum selesai.
	public BigDecimal reserved(String branchId, String productId, String excludeOrderId) {
		Object sum = entityManager.createNativeQuery("SELECT COALESCE(SUM(i.quantity), 0) FROM public.online_order_item i "
				+ "JOIN public.online_order o ON o.id = i.order_id WHERE o.branch_id = :branch AND i.product_id = :product "
				+ "AND o.status IN ('APPROVED', 'READY') AND o.id <> :exclude")
				.setParameter("branch", branchId).setParameter("product", productId)
				.setParameter("exclude", excludeOrderId == null ? "" : excludeOrderId).getSingleResult();
		return (BigDecimal) sum;
	}

	public long pendingByPhone(String branchId, String phone) {
		return ((Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM public.online_order WHERE branch_id = :branch AND customer_phone = :phone AND status = 'PENDING'")
				.setParameter("branch", branchId).setParameter("phone", phone).getSingleResult()).longValue();
	}

	public long pendingByBranch(String branchId) {
		return ((Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM public.online_order WHERE branch_id = :branch AND status = 'PENDING'")
				.setParameter("branch", branchId).getSingleResult()).longValue();
	}

	// ---------------------------------------------------------------- tulis

	// Nomor harian berurutan (ONL-yyMMdd-0001). Lock dilepas saat commit, jadi insert lain menunggu sampai nomor ini tersimpan.
	public String nextSerial() {
		entityManager.createNativeQuery("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext('online_order_serial'))) l").getSingleResult();
		String prefix = "ONL-" + LocalDate.now().format(SERIAL_DAY) + "-";
		Object latest = entityManager.createNativeQuery("SELECT MAX(serial) FROM public.online_order WHERE serial LIKE :prefix")
				.setParameter("prefix", prefix + "%").getSingleResult();
		int sequence = latest == null ? 1 : Integer.parseInt(((String) latest).substring(prefix.length())) + 1;
		return prefix + String.format("%04d", sequence);
	}

	public String insert(String serial, String token, String branchId, String fulfillment, String paymentMethod, String name, String phone,
			String address, String note, BigDecimal estimate) {
		String id = ID.uuid();
		LocalDateTime now = LocalDateTime.now();
		entityManager.createNativeQuery("INSERT INTO public.online_order (id, serial, token, branch_id, status, fulfillment, payment_method, "
				+ "customer_name, customer_phone, address, note, estimate_total, registered, modificated) VALUES (:id, :serial, :token, :branch, 'PENDING', "
				+ ":fulfillment, :payment, :name, :phone, :address, :note, :estimate, :now, :now)")
				.setParameter("id", id).setParameter("serial", serial).setParameter("token", token).setParameter("branch", branchId)
				.setParameter("fulfillment", fulfillment).setParameter("payment", paymentMethod).setParameter("name", name)
				.setParameter("phone", phone).setParameter("address", address).setParameter("note", note)
				.setParameter("estimate", estimate).setParameter("now", now).executeUpdate();
		return id;
	}

	public void insertItem(String orderId, String productId, String productName, String unit, BigDecimal quantity, BigDecimal unitPrice) {
		entityManager.createNativeQuery("INSERT INTO public.online_order_item (order_id, product_id, product_name, unit, quantity, unit_price) "
				+ "VALUES (:order, :product, :name, :unit, :quantity, :price)")
				.setParameter("order", orderId).setParameter("product", productId).setParameter("name", productName)
				.setParameter("unit", unit).setParameter("quantity", quantity).setParameter("price", unitPrice).executeUpdate();
	}

	public void insertEvent(String orderId, String status, String note, String actor) {
		entityManager.createNativeQuery("INSERT INTO public.online_order_event (id, order_id, status, note, actor, at) VALUES (:id, :order, :status, :note, :actor, :now)")
				.setParameter("id", ID.uuid()).setParameter("order", orderId).setParameter("status", status).setParameter("note", note)
				.setParameter("actor", actor).setParameter("now", LocalDateTime.now()).executeUpdate();
	}

	// Mengubah status; field keputusan hanya diisi bila decidedBy ada (setuju/tolak/batal oleh staf). confirmedTotal/saleId hanya bila diberikan.
	public void updateStatus(String id, String status, String decidedBy, String decisionNote, BigDecimal confirmedTotal, String saleId) {
		LocalDateTime now = LocalDateTime.now();
		entityManager.createNativeQuery("UPDATE public.online_order SET status = :status, modificated = :now, "
				+ "decided_by = COALESCE(CAST(:by AS varchar), decided_by), decided_at = CASE WHEN CAST(:by AS varchar) IS NULL THEN decided_at ELSE :now END, "
				+ "decision_note = CASE WHEN CAST(:by AS varchar) IS NULL THEN decision_note ELSE :note END, "
				+ "confirmed_total = COALESCE(CAST(:confirmed AS numeric), confirmed_total), sale_id = COALESCE(CAST(:sale AS varchar), sale_id) WHERE id = :id")
				.setParameter("status", status).setParameter("now", now).setParameter("by", decidedBy).setParameter("note", decisionNote)
				.setParameter("confirmed", confirmedTotal).setParameter("sale", saleId).setParameter("id", id).executeUpdate();
	}

	// ---------------------------------------------------------------- bantu

	private Row row(Object[] r) {
		return new Row((String) r[0], (String) r[1], (String) r[2], (String) r[3], (String) r[4], (String) r[5], (String) r[6], (String) r[7],
				(String) r[8], (String) r[9], (String) r[10], (BigDecimal) r[11], (BigDecimal) r[12], (String) r[13], (String) r[14],
				r[15] == null ? null : time(r[15]), (String) r[16], time(r[17]));
	}

	private static LocalDateTime time(Object value) {
		return value instanceof Timestamp t ? t.toLocalDateTime() : (LocalDateTime) value;
	}

	private static String like(String value) {
		return "%" + value.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
	}

}
