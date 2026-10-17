package com.saasno.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.utility.BranchScope;
import com.saasno.utility.dto.search.SearchResultDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.model.ResponseModel;

import jakarta.persistence.EntityManager;

// Pencarian global (Ctrl+K) untuk data yang boleh dilihat user: tiap sumber hanya dicari bila user punya izin
// membaca daftarnya (izin API yang sama dengan halamannya) dan dibatasi cakupan cabang yang sama dengan halaman itu.
// Halaman menu dicari di sisi frontend dari menu yang sudah difilter izin.
@Service
public class SearchService {

	private static final int MIN_LENGTH = 2;
	private static final int MAX_LENGTH = 50;
	private static final int PER_SOURCE = 5;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private PermissionService permissionService;

	@Autowired
	private BranchScopeService branchScopeService;

	@Transactional(readOnly = true)
	public ResponseModel<List<SearchResultDTO>> search(String query) {
		String text = query == null ? "" : query.trim();
		if (text.length() < MIN_LENGTH) {
			return new ResponseModel<List<SearchResultDTO>>().success(List.of());
		}
		if (text.length() > MAX_LENGTH) {
			throw new ApiException("400", "Kata kunci terlalu panjang");
		}
		BranchScope scope = branchScopeService.current();
		Set<String> apis = permissionService.apis();
		String like = "%" + text.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
		List<SearchResultDTO> results = new ArrayList<>();

		if (apis.contains("GET_API_PRODUCT")) {
			for (Object[] r : rows("SELECT p.serial, p.name, p.status FROM ProductEntity p WHERE (LOWER(p.serial) LIKE :q ESCAPE '\\' "
					+ "OR LOWER(p.name) LIKE :q ESCAPE '\\')" + (scope.isGlobal() ? "" : " AND p.branchId IN :ids")
					+ " ORDER BY p.name", like, scope.isGlobal() ? null : scope.getVisibleIds())) {
				results.add(new SearchResultDTO("PRODUCT", r[0] + " · " + r[1], "ACTIVE".equals(r[2]) ? "Produk" : "Produk nonaktif",
						link("/master/product", "serial", (String) r[0])));
			}
		}
		if (apis.contains("GET_API_CUSTOMER")) {
			for (Object[] r : rows("SELECT c.serial, c.profile.name FROM CustomerEntity c WHERE (LOWER(c.serial) LIKE :q ESCAPE '\\' "
					+ "OR LOWER(c.profile.name) LIKE :q ESCAPE '\\')" + (scope.isGlobal() ? "" : " AND c.branchId IN :ids")
					+ " ORDER BY c.profile.name", like, scope.isGlobal() ? null : scope.getIds())) {
				results.add(new SearchResultDTO("CUSTOMER", r[0] + " · " + r[1], "Customer", link("/master/customer", "serial", (String) r[0])));
			}
		}
		if (apis.contains("GET_API_SALE")) {
			for (Object[] r : rows("SELECT s.serial, s.status FROM SaleEntity s WHERE LOWER(s.serial) LIKE :q ESCAPE '\\'"
					+ (scope.isGlobal() ? "" : " AND s.branchId IN :ids") + " ORDER BY s.registered DESC", like,
					scope.isGlobal() ? null : scope.getIds())) {
				results.add(new SearchResultDTO("SALE", (String) r[0], "Transaksi · " + r[1], link("/transaction/history", "serial", (String) r[0])));
			}
		}
		if (apis.contains("GET_API_STOCK_TRANSFER")) {
			for (Object[] r : rows("SELECT t.serial, t.status FROM StockTransferEntity t WHERE LOWER(t.serial) LIKE :q ESCAPE '\\'"
					+ (scope.isGlobal() ? "" : " AND (t.sourceBranchId IN :ids OR t.destinationBranchId IN :ids)")
					+ " ORDER BY t.registered DESC", like, scope.isGlobal() ? null : scope.getIds())) {
				results.add(new SearchResultDTO("STOCK_TRANSFER", (String) r[0], "Oper stok · " + r[1],
						link("/stock/transfer", "serial", (String) r[0])));
			}
		}
		if (apis.contains("GET_API_STOCK_OPNAME")) {
			for (Object[] r : rows("SELECT o.serial, o.status FROM StockOpnameEntity o WHERE LOWER(o.serial) LIKE :q ESCAPE '\\'"
					+ (scope.isGlobal() ? "" : " AND o.branchId IN :ids") + " ORDER BY o.registered DESC", like,
					scope.isGlobal() ? null : scope.getIds())) {
				results.add(new SearchResultDTO("STOCK_OPNAME", (String) r[0], "Stok opname · " + r[1],
						link("/stock/opname", "serial", (String) r[0])));
			}
		}
		return new ResponseModel<List<SearchResultDTO>>().success(results);
	}

	@SuppressWarnings("unchecked")
	private List<Object[]> rows(String jpql, String like, java.util.Collection<String> ids) {
		var query = entityManager.createQuery(jpql, Object[].class).setParameter("q", like).setMaxResults(PER_SOURCE);
		if (ids != null) {
			query.setParameter("ids", ids);
		}
		return query.getResultList();
	}

	private String link(String path, String field, String value) {
		return path + "?" + field + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

}
