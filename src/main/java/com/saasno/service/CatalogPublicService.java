package com.saasno.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.repository.BranchRepository;
import com.saasno.utility.dto.catalog.PublicCatalogDTO;
import com.saasno.utility.dto.product.ProductUnitDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.model.ResponseModel;

import jakarta.persistence.EntityManager;

// Katalog yang dibaca tanpa login (/api/public/catalog). Aturan tampil: katalog cabang aktif; produk berstatus ACTIVE yang ada stoknya
// di cabang itu (stok fisik > 0, dari mana pun pemilik produknya), kecuali yang disembunyikan pemiliknya. Hanya data untuk umum yang keluar:
// harga dasar, deskripsi, dan apakah stok cabang itu ada, tanpa angka stok.
@Service
public class CatalogPublicService {

	private static final int DEFAULT_LIMIT = 24;
	private static final int MAX_LIMIT = 48;
	private static final int MAX_QUERY = 50;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private BranchRepository branchRepository;

	@Autowired
	private ProductUnitService productUnitService;

	public record Catalog(String branchId, String branchName, String whatsapp) {
	}

	// Daftar katalog aktif (alamat dan nama cabang) untuk halaman pilih katalog. Hanya yang memang diaktifkan pemiliknya.
	@Transactional(readOnly = true)
	@SuppressWarnings("unchecked")
	public ResponseModel<List<PublicCatalogDTO.Entry>> catalogs() {
		List<Object[]> rows = entityManager.createNativeQuery("SELECT bc.slug, b.name FROM public.branch_catalog bc "
				+ "JOIN public.branch b ON b.id = bc.branch_id WHERE bc.enabled ORDER BY LOWER(b.name), bc.slug").getResultList();
		List<PublicCatalogDTO.Entry> list = new ArrayList<>();
		for (Object[] r : rows) {
			list.add(new PublicCatalogDTO.Entry((String) r[0], (String) r[1]));
		}
		return new ResponseModel<List<PublicCatalogDTO.Entry>>().success(list);
	}

	@Transactional(readOnly = true)
	public ResponseModel<PublicCatalogDTO> catalog(String slug, String query, Integer offset, Integer limit) {
		Catalog catalog = find(slug);
		String keyword = query == null ? "" : query.trim();
		if (keyword.length() > MAX_QUERY) {
			throw new ApiException("400", "Kata kunci terlalu panjang");
		}
		int size = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(MAX_LIMIT, limit));
		int start = offset == null ? 0 : Math.max(0, offset);
		// Isi katalog = produk aktif yang ADA stoknya di cabang ini (stok fisik > 0), kecuali yang disembunyikan pemiliknya.
		String where = " FROM public.stock_balance sb JOIN public.product p ON p.id = sb.product_id "
				+ "LEFT JOIN public.product_catalog c ON c.product_id = p.id "
				+ "WHERE sb.branch_id = :branch AND sb.quantity > 0 AND p.status = 'ACTIVE' AND NOT COALESCE(c.hidden, false)"
				+ (keyword.isEmpty() ? "" : " AND (LOWER(p.name) LIKE :q ESCAPE '\\' OR LOWER(p.serial) LIKE :q ESCAPE '\\' "
						+ "OR LOWER(COALESCE(p.description, '')) LIKE :q ESCAPE '\\')");
		var count = entityManager.createNativeQuery("SELECT COUNT(*)" + where)
				.setParameter("branch", catalog.branchId());
		var select = entityManager.createNativeQuery("SELECT p.id, p.serial, p.name, p.description, p.price, p.unit, "
				+ "sb.quantity - COALESCE((SELECT SUM(i.quantity) FROM public.online_order_item i JOIN public.online_order o ON o.id = i.order_id "
				+ "WHERE o.branch_id = :branch AND i.product_id = p.id AND o.status IN ('APPROVED', 'READY')), 0), "
				+ "c.image_updated" + where + " ORDER BY LOWER(p.name), p.id")
				.setParameter("branch", catalog.branchId())
				.setFirstResult(start).setMaxResults(size);
		if (!keyword.isEmpty()) {
			String like = "%" + keyword.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
			count.setParameter("q", like);
			select.setParameter("q", like);
		}
		long total = ((Number) count.getSingleResult()).longValue();

		Map<String, String> unitLabels = new HashMap<>();
		for (ProductUnitDTO unit : productUnitService.units()) {
			unitLabels.put(unit.getValue(), unit.getLabel());
		}
		List<PublicCatalogDTO.Product> products = new ArrayList<>();
		@SuppressWarnings("unchecked")
		List<Object[]> rows = select.getResultList();
		for (Object[] r : rows) {
			products.add(product(r, unitLabels));
		}
		return new ResponseModel<PublicCatalogDTO>().success(
				new PublicCatalogDTO(catalog.branchName(), slug, catalog.whatsapp(), total, products));
	}

	private static final BigDecimal LIMITED_AT = BigDecimal.TEN;

	// Satu baris hasil (id, serial, nama, deskripsi, harga, satuan, sisa bebas, versi foto) menjadi produk publik.
	private PublicCatalogDTO.Product product(Object[] r, Map<String, String> unitLabels) {
		BigDecimal free = (BigDecimal) r[6];
		boolean available = free.compareTo(BigDecimal.ONE) >= 0;
		boolean limited = available && free.compareTo(LIMITED_AT) <= 0;
		return new PublicCatalogDTO.Product((String) r[0], (String) r[1], (String) r[2], (String) r[3], (BigDecimal) r[4], (String) r[5],
				unitLabels.getOrDefault((String) r[5], (String) r[5]), available, limited, limited ? free.intValue() : null,
				r[7] == null ? null : CatalogService.version(r[7]));
	}

	// Keadaan terkini beberapa produk sekaligus (untuk mencocokkan keranjang pembeli dengan katalog). Hanya yang SEKARANG tampil di
	// katalog ini yang dikembalikan; id yang tidak ada di hasil berarti sudah tidak tersedia. Maksimal 50 id.
	@Transactional(readOnly = true)
	@SuppressWarnings("unchecked")
	public ResponseModel<List<PublicCatalogDTO.Product>> current(String slug, List<String> ids) {
		Catalog catalog = find(slug);
		List<String> wanted = ids == null ? List.of() : ids.stream().filter(i -> i != null && !i.isBlank() && i.length() <= 64).distinct().limit(50).toList();
		if (wanted.isEmpty()) {
			return new ResponseModel<List<PublicCatalogDTO.Product>>().success(List.of());
		}
		List<Object[]> rows = entityManager.createNativeQuery("SELECT p.id, p.serial, p.name, p.description, p.price, p.unit, "
				+ "sb.quantity - COALESCE((SELECT SUM(i.quantity) FROM public.online_order_item i JOIN public.online_order o ON o.id = i.order_id "
				+ "WHERE o.branch_id = :branch AND i.product_id = p.id AND o.status IN ('APPROVED', 'READY')), 0), c.image_updated "
				+ "FROM public.stock_balance sb JOIN public.product p ON p.id = sb.product_id LEFT JOIN public.product_catalog c ON c.product_id = p.id "
				+ "WHERE sb.branch_id = :branch AND sb.quantity > 0 AND p.status = 'ACTIVE' AND NOT COALESCE(c.hidden, false) AND p.id IN (:ids) "
				+ "ORDER BY LOWER(p.name), p.id").setParameter("branch", catalog.branchId()).setParameter("ids", wanted).getResultList();
		Map<String, String> unitLabels = new HashMap<>();
		for (ProductUnitDTO unit : productUnitService.units()) {
			unitLabels.put(unit.getValue(), unit.getLabel());
		}
		return new ResponseModel<List<PublicCatalogDTO.Product>>().success(rows.stream().map(r -> product(r, unitLabels)).toList());
	}

	// Waktu foto terakhir diganti (epoch milidetik) bila produk tampil di katalog ini dan punya foto; selain itu null.
	@Transactional(readOnly = true)
	@SuppressWarnings("unchecked")
	public Long imageVersion(String slug, String productId) {
		Catalog catalog = find(slug);
		List<Object> rows = entityManager.createNativeQuery("SELECT c.image_updated FROM public.stock_balance sb "
				+ "JOIN public.product p ON p.id = sb.product_id JOIN public.product_catalog c ON c.product_id = p.id "
				+ "WHERE sb.branch_id = :branch AND sb.quantity > 0 AND p.id = :id AND p.status = 'ACTIVE' AND NOT c.hidden AND c.image_updated IS NOT NULL")
				.setParameter("id", productId).setParameter("branch", catalog.branchId()).getResultList();
		return rows.isEmpty() ? null : CatalogService.version(rows.get(0));
	}

	@SuppressWarnings("unchecked")
	public Catalog find(String slug) {
		List<Object[]> rows = slug == null ? List.of() : entityManager.createNativeQuery(
				"SELECT bc.branch_id, b.name, bc.whatsapp FROM public.branch_catalog bc JOIN public.branch b ON b.id = bc.branch_id "
						+ "WHERE bc.slug = :slug AND bc.enabled").setParameter("slug", slug).getResultList();
		if (rows.isEmpty()) {
			throw new ApiException("404", "Katalog tidak ditemukan");
		}
		return new Catalog((String) rows.get(0)[0], (String) rows.get(0)[1], (String) rows.get(0)[2]);
	}

}
