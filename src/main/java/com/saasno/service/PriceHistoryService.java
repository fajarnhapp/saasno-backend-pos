package com.saasno.service;

import static com.saasno.service.AnalyticsSupport.dec;
import static com.saasno.service.AnalyticsSupport.lng;
import static com.saasno.service.AnalyticsSupport.str;
import static com.saasno.service.AnalyticsSupport.time;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.entity.ProductEntity;
import com.saasno.utility.BranchScope;
import com.saasno.utility.dto.product.PriceHistoryDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.generator.ID;
import com.saasno.utility.model.data.DataResult;

// Riwayat harga jual dasar dan harga pokok produk. Dicatat hanya bila nilainya benar-benar berubah (atau diisi pertama kali).
@Service
public class PriceHistoryService {

	public static final String PRICE = "PRICE";
	public static final String COST_PRICE = "COST_PRICE";
	public static final String MANUAL = "MANUAL";
	public static final String PURCHASE = "PURCHASE";
	public static final String IMPORT = "IMPORT";
	private static final int MAX_PAGE = 100;

	@Autowired
	private SqlHelper sql;

	@Autowired
	private BranchScopeService branchScopeService;

	@Autowired
	private com.saasno.repository.ProductRepository productRepository;

	// Mencatat perubahan; tidak melakukan apa pun bila nilai lama dan baru sama.
	public void record(String productId, String field, BigDecimal oldValue, BigDecimal newValue, String source, String userId, String note) {
		boolean same = oldValue == null ? newValue == null : newValue != null && oldValue.compareTo(newValue) == 0;
		if (same) {
			return;
		}
		sql.execute("INSERT INTO public.product_price_history (id, product_id, field, old_value, new_value, source, changed_by, changed_at, note) "
				+ "VALUES (:id, :product, :field, :old, :new, :source, :user, :now, :note)",
				"id", ID.uuid(), "product", productId, "field", field, "old", oldValue, "new", newValue, "source", source, "user", userId,
				"now", LocalDateTime.now(), "note", note);
	}

	public void record(ProductEntity product, BigDecimal oldPrice, BigDecimal oldCost, String source, String userId) {
		record(product.getId(), PRICE, oldPrice, product.getPrice(), source, userId, null);
		record(product.getId(), COST_PRICE, oldCost, product.getCostPrice(), source, userId, null);
	}

	@Transactional(readOnly = true)
	public ResponseEntity<DataResult<PriceHistoryDTO>> read(String productId, Integer offset, Integer limit) {
		BranchScope scope = branchScopeService.current();
		ProductEntity product = productId == null ? null : productRepository.find(productId.trim());
		if (product == null || !scope.canSee(product.getBranchId())) {
			throw new ApiException("404", "Produk tidak ditemukan");
		}
		int size = limit == null || limit < 1 ? 10 : Math.min(limit, MAX_PAGE);
		int start = offset == null || offset < 0 ? 0 : offset;
		long total = lng(sql.rows("SELECT COUNT(*) FROM public.product_price_history h WHERE h.product_id = :p", "p", product.getId()).get(0)[0]);
		List<PriceHistoryDTO> list = new ArrayList<>();
		for (Object[] r : sql.rows("SELECT h.id, h.field, h.old_value, h.new_value, h.source, u.username, h.changed_at, h.note FROM public.product_price_history h "
				+ "JOIN public.\"user\" u ON u.id = h.changed_by WHERE h.product_id = :p ORDER BY h.changed_at DESC, h.field LIMIT " + size + " OFFSET " + start,
				"p", product.getId())) {
			list.add(new PriceHistoryDTO(str(r[0]), str(r[1]), r[2] == null ? null : dec(r[2]), r[3] == null ? null : dec(r[3]), str(r[4]), str(r[5]),
					time(r[6]), str(r[7])));
		}
		return ResponseEntity.ok(new DataResult<>(list, BigInteger.valueOf(total), BigInteger.valueOf(total)));
	}
}
