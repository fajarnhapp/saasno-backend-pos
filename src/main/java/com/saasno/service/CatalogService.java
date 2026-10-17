package com.saasno.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.saasno.entity.BranchEntity;
import com.saasno.entity.ProductEntity;
import com.saasno.repository.ProductRepository;
import com.saasno.utility.BranchScope;
import com.saasno.utility.WhatsappNumber;
import com.saasno.utility.dto.catalog.CatalogProductDTO;
import com.saasno.utility.dto.catalog.CatalogSettingDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.logger.Log;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataResult;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;

// Pengaturan katalog publik oleh pemilik cabang: alamat, nomor WhatsApp, aktif atau tidak, dan produk mana yang tampil beserta
// deskripsi dan fotonya. Semuanya hanya untuk cabang user sendiri dan produk milik cabang itu (produk induk diatur oleh induk;
// cabang bawah ikut menampilkannya bila induk menandainya tampil). Cabang lain dijawab seperti tidak ada.
@Service
public class CatalogService {

	private static final int MAX_DESCRIPTION = 1000;
	private static final int MAX_PAGE = 100;
	private static final java.util.regex.Pattern SLUG = java.util.regex.Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private BranchScopeService branchScopeService;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private CatalogImageService imageService;

	// ---------------------------------------------------------------- pengaturan cabang

	@Transactional(readOnly = true)
	@SuppressWarnings("unchecked")
	public ResponseModel<CatalogSettingDTO> setting(String requestedBranchId) {
		BranchEntity branch = ownBranch(requestedBranchId);
		List<Object[]> rows = entityManager.createNativeQuery(
				"SELECT slug, whatsapp, enabled FROM public.branch_catalog WHERE branch_id = :branch")
				.setParameter("branch", branch.getId()).getResultList();
		CatalogSettingDTO dto = rows.isEmpty()
				? new CatalogSettingDTO(branch.getId(), branch.getName(), null, null, false)
				: new CatalogSettingDTO(branch.getId(), branch.getName(), (String) rows.get(0)[0], (String) rows.get(0)[1],
						(Boolean) rows.get(0)[2]);
		return new ResponseModel<CatalogSettingDTO>().success(dto);
	}

	@Transactional
	@SuppressWarnings("unchecked")
	public ResponseModel<CatalogSettingDTO> saveSetting(CatalogSettingDTO request) {
		BranchEntity branch = ownBranch(request.branchId());
		String slug = request.slug() == null ? "" : request.slug().trim().toLowerCase();
		if (slug.length() < 3 || slug.length() > 40 || !SLUG.matcher(slug).matches()) {
			throw new ApiException("400",
					"Alamat katalog 3-40 karakter: huruf kecil, angka, dan tanda hubung di tengah (contoh toko-maju)");
		}
		String whatsapp = WhatsappNumber.normalize(request.whatsapp());
		boolean enabled = Boolean.TRUE.equals(request.enabled());

		List<String> taken = entityManager.createNativeQuery("SELECT branch_id FROM public.branch_catalog WHERE slug = :slug")
				.setParameter("slug", slug).getResultList();
		if (!taken.isEmpty() && !taken.get(0).equals(branch.getId())) {
			throw new ApiException("409", "Alamat katalog sudah dipakai cabang lain");
		}
		LocalDateTime now = LocalDateTime.now();
		entityManager.createNativeQuery("INSERT INTO public.branch_catalog (branch_id, slug, whatsapp, enabled, modificated, registered) "
				+ "VALUES (:branch, :slug, :whatsapp, :enabled, :now, :now) ON CONFLICT (branch_id) DO UPDATE "
				+ "SET slug = EXCLUDED.slug, whatsapp = EXCLUDED.whatsapp, enabled = EXCLUDED.enabled, modificated = EXCLUDED.modificated")
				.setParameter("branch", branch.getId()).setParameter("slug", slug).setParameter("whatsapp", whatsapp)
				.setParameter("enabled", enabled).setParameter("now", now).executeUpdate();
		return new ResponseModel<CatalogSettingDTO>().success(new CatalogSettingDTO(branch.getId(), branch.getName(), slug, whatsapp, enabled));
	}

	// ---------------------------------------------------------------- produk

	// Produk milik cabang (bukan warisan induk), diurutkan menurut nama. Pencarian: name atau serial berisi kata kunci.
	@Transactional(readOnly = true)
	@SuppressWarnings("unchecked")
	public ResponseEntity<DataResult<CatalogProductDTO>> products(HttpServletRequest request) {
		try {
			BranchEntity branch = ownBranch(request.getParameter("branchId"));
			String keyword = clean(request.getParameter("name"));
			String serial = clean(request.getParameter("serial"));
			int limit = clamp(parse(request.getParameter("limit"), 10), 1, MAX_PAGE);
			int offset = Math.max(0, parse(request.getParameter("offset"), 0));
			String where = " FROM public.product p LEFT JOIN public.product_catalog c ON c.product_id = p.id "
					+ "LEFT JOIN public.stock_balance sb ON sb.branch_id = :branch AND sb.product_id = p.id WHERE p.branch_id = :branch"
					+ (keyword == null ? "" : " AND LOWER(p.name) LIKE :name ESCAPE '\\'")
					+ (serial == null ? "" : " AND LOWER(p.serial) LIKE :serial ESCAPE '\\'");
			var count = entityManager.createNativeQuery("SELECT COUNT(*)" + where).setParameter("branch", branch.getId());
			var select = entityManager.createNativeQuery("SELECT p.id, p.serial, p.name, p.price, p.unit, p.status, NOT COALESCE(c.hidden, false), "
					+ "p.description, c.image_updated, COALESCE(sb.quantity, 0) > 0" + where + " ORDER BY LOWER(p.name), p.id")
					.setParameter("branch", branch.getId()).setFirstResult(offset).setMaxResults(limit);
			if (keyword != null) {
				count.setParameter("name", like(keyword));
				select.setParameter("name", like(keyword));
			}
			if (serial != null) {
				count.setParameter("serial", like(serial));
				select.setParameter("serial", like(serial));
			}
			BigInteger total = BigInteger.valueOf(((Number) count.getSingleResult()).longValue());
			List<CatalogProductDTO> list = new ArrayList<>();
			for (Object[] r : (List<Object[]>) select.getResultList()) {
				Long version = r[8] == null ? null : version(r[8]);
				list.add(new CatalogProductDTO((String) r[0], (String) r[1], (String) r[2], (BigDecimal) r[3], (String) r[4],
						(String) r[5], (Boolean) r[6], (String) r[7], version != null, version, (Boolean) r[9]));
			}
			return new ResponseEntity<>(new DataResult<>(list, total, total), HttpStatus.OK);
		} catch (ApiException e) {
			throw e;
		} catch (Exception e) {
			Log.error(e.getMessage(), e);
			return new ResponseEntity<>(new DataResult<CatalogProductDTO>(BigInteger.ZERO, BigInteger.ZERO), HttpStatus.OK);
		}
	}

	@Transactional
	public ResponseModel<CatalogProductDTO> saveProduct(CatalogProductDTO request) {
		ProductEntity product = owned(request.id());
		String description = clean(request.description());
		if (description != null && description.length() > MAX_DESCRIPTION) {
			throw new ApiException("400", "Deskripsi tidak boleh lebih dari " + MAX_DESCRIPTION + " karakter");
		}
		boolean visible = Boolean.TRUE.equals(request.visible());
		LocalDateTime now = LocalDateTime.now();
		entityManager.createNativeQuery("INSERT INTO public.product_catalog (product_id, hidden, modificated, registered) "
				+ "VALUES (:product, :hidden, :now, :now) ON CONFLICT (product_id) DO UPDATE "
				+ "SET hidden = EXCLUDED.hidden, modificated = EXCLUDED.modificated")
				.setParameter("product", product.getId()).setParameter("hidden", !visible).setParameter("now", now).executeUpdate();
		// Deskripsi milik produk (juga diinput di halaman Produk); dialog katalog menyunting kolom yang sama.
		entityManager.createNativeQuery("UPDATE public.product SET description = :description, modificated = :now WHERE id = :product")
				.setParameter("description", description).setParameter("now", now).setParameter("product", product.getId()).executeUpdate();
		return new ResponseModel<CatalogProductDTO>().success(load(product));
	}

	@Transactional
	public ResponseModel<CatalogProductDTO> uploadImage(String productId, MultipartFile file) {
		ProductEntity product = owned(productId);
		byte[] jpeg = imageService.normalize(file);
		imageService.save(product.getId(), jpeg);
		LocalDateTime now = LocalDateTime.now();
		entityManager.createNativeQuery("INSERT INTO public.product_catalog (product_id, image_updated, modificated, registered) "
				+ "VALUES (:product, :now, :now, :now) ON CONFLICT (product_id) DO UPDATE "
				+ "SET image_updated = EXCLUDED.image_updated, modificated = EXCLUDED.modificated")
				.setParameter("product", product.getId()).setParameter("now", now).executeUpdate();
		return new ResponseModel<CatalogProductDTO>().success(load(product));
	}

	// Foto untuk pratinjau di halaman pengaturan (butuh login), tersedia walau katalog belum diaktifkan. Null bila belum ada foto.
	@Transactional(readOnly = true)
	public byte[] image(String productId) {
		ProductEntity product = owned(productId);
		return imageService.read(product.getId());
	}

	@Transactional
	public ResponseModel<CatalogProductDTO> deleteImage(String productId) {
		ProductEntity product = owned(productId);
		imageService.delete(product.getId());
		entityManager.createNativeQuery("UPDATE public.product_catalog SET image_updated = NULL, modificated = :now WHERE product_id = :product")
				.setParameter("product", product.getId()).setParameter("now", LocalDateTime.now()).executeUpdate();
		return new ResponseModel<CatalogProductDTO>().success(load(product));
	}

	// ---------------------------------------------------------------- bantu

	// Cabang tempat katalog diatur: user cabang selalu cabangnya sendiri (403 bila menyebut yang lain), user global menyebutkannya.
	private BranchEntity ownBranch(String requested) {
		BranchScope scope = branchScopeService.current();
		return branchScopeService.ownBranch(scope, requested, "Katalog hanya dapat diatur untuk cabang Anda sendiri");
	}

	// Produk yang dimiliki cabang user (bukan warisan induk, bukan cabang lain).
	private ProductEntity owned(String id) {
		BranchScope scope = branchScopeService.current();
		ProductEntity product = id == null ? null : productRepository.find(id.trim());
		if (product == null || !scope.contains(product.getBranchId())
				|| (!scope.isGlobal() && !product.getBranchId().equals(scope.getBranchId()))) {
			throw new ApiException("404", "Produk tidak ditemukan");
		}
		return product;
	}

	@SuppressWarnings("unchecked")
	private CatalogProductDTO load(ProductEntity product) {
		entityManager.flush();
		List<Object[]> rows = entityManager.createNativeQuery("SELECT NOT COALESCE(c.hidden, false), p.description, c.image_updated, "
				+ "COALESCE((SELECT sb.quantity FROM public.stock_balance sb WHERE sb.branch_id = p.branch_id AND sb.product_id = p.id), 0) > 0 "
				+ "FROM public.product p LEFT JOIN public.product_catalog c ON c.product_id = p.id WHERE p.id = :id")
				.setParameter("id", product.getId()).getResultList();
		Object[] r = rows.get(0);
		Long version = r[2] == null ? null : version(r[2]);
		return new CatalogProductDTO(product.getId(), product.getSerial(), product.getName(), product.getPrice(), product.getUnit(),
				product.getStatus(), (Boolean) r[0], (String) r[1], version != null, version, (Boolean) r[3]);
	}

	// Waktu foto terakhir diganti dalam epoch milidetik (zona JVM, sama dengan penulisan waktu aplikasi).
	static Long version(Object value) {
		LocalDateTime time = value instanceof Timestamp t ? t.toLocalDateTime() : (LocalDateTime) value;
		return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
	}

	private String like(String value) {
		return "%" + value.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
	}

	private String clean(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private int parse(String value, int fallback) {
		try {
			return value == null ? fallback : Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

}
