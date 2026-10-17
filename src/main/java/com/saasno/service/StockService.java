package com.saasno.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.entity.ProductEntity;
import com.saasno.entity.ProductUnitConversionEntity;
import com.saasno.entity.StockBalanceEntity;
import com.saasno.entity.StockMovementEntity;
import com.saasno.repository.ProductRepository;
import com.saasno.repository.ProductUnitConversionRepository;
import com.saasno.repository.StockRepository;
import com.saasno.repository.UserRepository;
import com.saasno.utility.BranchScope;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.dto.stock.StockChangeDTO;
import com.saasno.utility.dto.stock.StockDTO;
import com.saasno.utility.dto.stock.StockMovementDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.logger.Log;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;

// Stok per cabang. Aturan akses: user hanya melihat/mengubah stok di cabangnya dan turunannya, dan hanya
// untuk produk yang terlihat oleh cabang itu (milik cabang itu, turunannya, atau induknya).
@Service
public class StockService {

	private static final BigDecimal MAX_QUANTITY = new BigDecimal("9999999999999.999999");
	private static final int SCALE = 6;
	private static final int MAX_NOTE = 500;

	@Autowired
	private StockRepository stockRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private ProductUnitConversionRepository conversionRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private BranchScopeService branchScopeService;

	@Autowired
	private StockAlertService stockAlertService;

	@Autowired
	private SessionUtility sessionUtility;

	// ---------------------------------------------------------------- baca

	@Transactional(readOnly = true)
	public ResponseEntity<DataResult<StockDTO>> read(HttpServletRequest request) {
		try {
			return new ResponseEntity<>(find(new DataFilter(request), branchScopeService.current()), HttpStatus.OK);
		} catch (ApiException e) {
			throw e;
		} catch (Exception e) {
			Log.error(e.getMessage(), e);
			return new ResponseEntity<>(new DataResult<StockDTO>(BigInteger.ZERO, BigInteger.ZERO), HttpStatus.OK);
		}
	}

	// Isi read untuk satu filter dan cakupan cabang (dipakai juga oleh pengunduhan).
	@Transactional(readOnly = true)
	public DataResult<StockDTO> find(DataFilter requested, BranchScope scope) {
		DataFilter filter = scope.apply(requested, "branchId");
		if (filter.getOrder() == null) {
			filter.setOrder("modificated");
			filter.setDescending(true);
		}
		DataResult<StockBalanceEntity> result = stockRepository.balances(filter);
		Map<String, String> branchNames = branchScopeService.names(
				result.getData().stream().map(StockBalanceEntity::getBranchId).collect(Collectors.toSet()));
		List<StockDTO> dto = result.getData().stream().map(b -> {
			StockDTO item = new StockDTO();
			item.setBranchId(b.getBranchId());
			item.setBranchName(branchNames.get(b.getBranchId()));
			item.setProductId(b.getProductId());
			item.setProductSerial(b.getProduct().getSerial());
			item.setProductName(b.getProduct().getName());
			item.setUnit(b.getProduct().getUnit());
			item.setQuantity(b.getQuantity());
			item.setModificated(b.getModificated());
			return item;
		}).collect(Collectors.toList());
		return new DataResult<>(dto, result.getTotal(), result.getGrandTotal());
	}

	@Transactional(readOnly = true)
	public ResponseEntity<DataResult<StockMovementDTO>> readMovements(HttpServletRequest request) {
		try {
			return new ResponseEntity<>(findMovements(new DataFilter(request), branchScopeService.current()), HttpStatus.OK);
		} catch (ApiException e) {
			throw e;
		} catch (Exception e) {
			Log.error(e.getMessage(), e);
			return new ResponseEntity<>(new DataResult<StockMovementDTO>(BigInteger.ZERO, BigInteger.ZERO),
					HttpStatus.OK);
		}
	}

	// Isi readMovements untuk satu filter dan cakupan cabang (dipakai juga oleh pengunduhan).
	@Transactional(readOnly = true)
	public DataResult<StockMovementDTO> findMovements(DataFilter requested, BranchScope scope) {
		DataFilter filter = scope.apply(requested, "branchId");
		if (filter.getOrder() == null) {
			filter.setOrder("registered");
			filter.setDescending(true);
		}
		DataResult<StockMovementEntity> result = stockRepository.movements(filter);
		Map<String, String> branchNames = branchScopeService.names(
				result.getData().stream().map(StockMovementEntity::getBranchId).collect(Collectors.toSet()));
		Map<String, String> usernames = userRepository.findUsernames(
				result.getData().stream().map(StockMovementEntity::getCreatedBy).collect(Collectors.toSet()));
		List<StockMovementDTO> dto = result.getData().stream().map(m -> {
			StockMovementDTO item = new StockMovementDTO();
			item.setId(m.getId());
			item.setBranchId(m.getBranchId());
			item.setBranchName(branchNames.get(m.getBranchId()));
			item.setProductId(m.getProductId());
			item.setProductSerial(m.getProduct().getSerial());
			item.setProductName(m.getProduct().getName());
			item.setUnit(m.getProduct().getUnit());
			item.setType(m.getType());
			item.setQuantity(m.getQuantity());
			item.setBalanceAfter(m.getBalanceAfter());
			item.setReferenceType(m.getReferenceType());
			item.setReferenceId(m.getReferenceId());
			item.setNote(m.getNote());
			item.setCreatedBy(m.getCreatedBy());
			item.setCreatedByName(usernames.get(m.getCreatedBy()));
			item.setRegistered(m.getRegistered());
			return item;
		}).collect(Collectors.toList());
		return new DataResult<>(dto, result.getTotal(), result.getGrandTotal());
	}

	// ---------------------------------------------------------------- tulis

	// Stok masuk (stok awal, barang datang): menambah saldo, wajib ada catatan.
	@Transactional
	public ResponseModel<StockDTO> receive(StockChangeDTO request) {
		BranchScope scope = branchScopeService.current();
		ProductEntity product = stockableProduct(scope, request.getBranchId(), request.getProductId());
		String note = note(request.getNote());
		assertActive(product);
		BigDecimal quantity = toBase(product, request.getQuantity(), request.getUnit(), false);

		applyMovement(request.getBranchId().trim(), product, quantity, StockMovementEntity.RECEIVE, null, null, note,
				sessionUtility.getUserId());
		return new ResponseModel<StockDTO>().success(balance(request.getBranchId().trim(), product));
	}

	// Koreksi (opname): quantity adalah jumlah hasil hitung; sistem mencatat selisihnya. Wajib catatan.
	@Transactional
	public ResponseModel<StockDTO> adjust(StockChangeDTO request) {
		BranchScope scope = branchScopeService.current();
		ProductEntity product = stockableProduct(scope, request.getBranchId(), request.getProductId());
		String note = note(request.getNote());
		BigDecimal counted = toBase(product, request.getQuantity(), request.getUnit(), true);
		String branchId = request.getBranchId().trim();

		// Baris dikunci sampai transaksi selesai, jadi selisih dihitung dari saldo yang pasti.
		BigDecimal current = stockRepository.lockQuantity(branchId, product.getId());
		BigDecimal delta = counted.subtract(current);
		if (delta.signum() == 0) {
			throw new ApiException("400", "Jumlah hasil hitung sama dengan stok saat ini");
		}
		if (delta.signum() > 0) {
			assertActive(product);
		}
		applyMovement(branchId, product, delta, StockMovementEntity.ADJUST, null, null, note,
				sessionUtility.getUserId());
		return new ResponseModel<StockDTO>().success(balance(branchId, product));
	}

	// Mengubah saldo dan mencatat mutasinya dalam transaksi pemanggil. delta > 0 menambah, delta < 0
	// mengurangi (ditolak 409 kalau stok tidak cukup, saldo tidak pernah minus).
	@Transactional
	public void applyMovement(String branchId, ProductEntity product, BigDecimal delta, String type,
			String referenceType, String referenceId, String note, String userId) {
		applyMovement(branchId, product, delta, type, referenceType, referenceId, note, userId, null);
	}

	// unitCost: harga pokok per satuan dasar saat mutasi (penjualan, retur, pemakaian); null = tidak diketahui.
	@Transactional
	public void applyMovement(String branchId, ProductEntity product, BigDecimal delta, String type,
			String referenceType, String referenceId, String note, String userId, BigDecimal unitCost) {
		BigDecimal after = delta.signum() > 0
				? stockRepository.increase(branchId, product.getId(), delta)
				: stockRepository.decrease(branchId, product.getId(), delta.negate());
		if (after == null) {
			throw new ApiException("409", "Stok tidak cukup untuk " + product.getSerial() + " " + product.getName());
		}
		StockMovementEntity movement = new StockMovementEntity();
		movement.setBranchId(branchId);
		movement.setProductId(product.getId());
		movement.setType(type);
		movement.setQuantity(delta);
		movement.setBalanceAfter(after);
		movement.setUnitCost(unitCost);
		movement.setReferenceType(referenceType);
		movement.setReferenceId(referenceId);
		movement.setNote(note);
		movement.setCreatedBy(userId);
		stockRepository.addMovement(movement);
		if (delta.signum() < 0) {
			stockAlertService.onDecrease(branchId, product, after.subtract(delta), after, userId);
		}
	}

	// ---------------------------------------------------------------- validasi bersama

	// Cabang harus dalam cakupan user dan produk harus terlihat oleh cabang itu. Yang di luar aturan
	// dijawab seperti tidak ada.
	public ProductEntity stockableProduct(BranchScope scope, String branchId, String productId) {
		String branch = branchId == null ? null : branchId.trim();
		if (branch == null || branch.isEmpty() || !scope.contains(branch)) {
			throw new ApiException("404", "Cabang tidak ditemukan");
		}
		return visibleProduct(branch, productId);
	}

	// Produk harus ada dan terlihat oleh cabang tersebut (milik cabang itu, turunannya, atau induknya).
	public ProductEntity visibleProduct(String branchId, String productId) {
		ProductEntity product = productId == null ? null : productRepository.find(productId.trim());
		if (product == null || !branchScopeService.subtree(branchId).canSee(product.getBranchId())) {
			throw new ApiException("404", "Produk tidak ditemukan");
		}
		return product;
	}

	public void assertActive(ProductEntity product) {
		if ("DISABLED".equals(product.getStatus())) {
			throw new ApiException("409", "Produk nonaktif");
		}
	}

	// Mengubah jumlah input (dalam satuan dasar atau satuan konversi produk) menjadi satuan dasar.
	// allowZero: koreksi boleh menghitung 0.
	public BigDecimal toBase(ProductEntity product, BigDecimal quantity, String unit, boolean allowZero) {
		if (quantity == null || quantity.signum() < 0 || (!allowZero && quantity.signum() == 0)
				|| quantity.stripTrailingZeros().scale() > SCALE) {
			throw new ApiException("400", "Jumlah tidak valid");
		}
		String requested = unit == null || unit.isBlank() ? null : unit.trim();
		BigDecimal base = quantity;
		if (requested != null && !requested.equals(product.getUnit())) {
			BigDecimal factor = null;
			for (ProductUnitConversionEntity c : conversionRepository.findByProductIds(List.of(product.getId()))) {
				if (c.getToUnit().equals(requested)) {
					factor = c.getValue();
				}
			}
			if (factor == null) {
				throw new ApiException("400", "Satuan tidak memiliki konversi untuk produk ini");
			}
			base = quantity.multiply(factor);
		}
		try {
			base = base.setScale(SCALE, RoundingMode.UNNECESSARY);
		} catch (ArithmeticException e) {
			throw new ApiException("400", "Jumlah tidak dapat dikonversi tepat ke satuan dasar");
		}
		if (base.compareTo(MAX_QUANTITY) > 0 || (!allowZero && base.signum() <= 0)) {
			throw new ApiException("400", "Jumlah tidak valid");
		}
		return base;
	}

	// Catatan wajib untuk stok masuk dan koreksi.
	public String note(String value) {
		String note = value == null ? null : value.trim();
		if (note == null || note.isEmpty()) {
			throw new ApiException("400", "Catatan tidak boleh kosong");
		}
		if (note.length() > MAX_NOTE) {
			throw new ApiException("400", "Catatan tidak boleh lebih dari " + MAX_NOTE + " karakter");
		}
		return note;
	}

	private StockDTO balance(String branchId, ProductEntity product) {
		StockDTO dto = new StockDTO();
		dto.setBranchId(branchId);
		dto.setBranchName(branchScopeService.names(Set.of(branchId)).get(branchId));
		dto.setProductId(product.getId());
		dto.setProductSerial(product.getSerial());
		dto.setProductName(product.getName());
		dto.setUnit(product.getUnit());
		dto.setQuantity(stockRepository.quantity(branchId, product.getId()));
		return dto;
	}

	// Dipakai layanan lain untuk memastikan himpunan produk unik.
	public static <T> void assertUnique(List<T> items, String message) {
		if (new HashSet<>(items).size() != items.size()) {
			throw new ApiException("400", message);
		}
	}

}
