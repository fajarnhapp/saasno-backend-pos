package com.saasno.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.entity.CustomerEntity;
import com.saasno.entity.ProductCustomerPriceEntity;
import com.saasno.entity.ProductEntity;
import com.saasno.entity.ProductPriceTierEntity;
import com.saasno.entity.ProductUnitConversionEntity;
import com.saasno.repository.CustomerRepository;
import com.saasno.repository.ProductCustomerPriceRepository;
import com.saasno.repository.ProductPriceTierRepository;
import com.saasno.repository.ProductRepository;
import com.saasno.repository.ProductUnitConversionRepository;
import com.saasno.repository.PromoRepository;
import com.saasno.utility.BranchScope;
import com.saasno.utility.dto.product.ProductCustomerPriceDTO;
import com.saasno.utility.dto.product.ProductDTO;
import com.saasno.utility.dto.product.ProductPriceResultDTO;
import com.saasno.utility.dto.product.ProductPriceTierDTO;
import com.saasno.utility.dto.product.ProductUnitConversionDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.logger.Log;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;
import com.saasno.utility.picker.CustomerType;
import com.saasno.utility.picker.PriceTarget;
import com.saasno.utility.picker.ProductStatus;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class ProductService {

	@Autowired
	private BatchService batchService;

	@Autowired
	private PriceHistoryService priceHistoryService;

	@Autowired
	private com.saasno.utility.SessionUtility sessionUtility;

	// Bentuk kode satuan; daftar satuan yang boleh dipakai ada di configuration PRODUCT_UNIT.
	private static final Pattern UNIT = Pattern.compile("^[A-Z0-9_]{1,32}$");
	private static final BigDecimal MAX_PRICE = new BigDecimal("9999999999999999.99");
	private static final BigDecimal MAX_CONVERSION = new BigDecimal("9999999999999.999999");
	private static final int CONVERSION_SCALE = 6;

	// Tingkat harga yang sudah tervalidasi, urut dari pembelian terkecil.
	private record Tier(BigDecimal minQuantity, BigDecimal price) {
	}

	// Harga khusus yang sudah tervalidasi. key = "TYPE:INDIVIDU" atau "CUSTOMER:<id>".
	private record CustomerPrice(String key, String target, String customerType, String customerId, BigDecimal price) {
	}

	private static final int MAX_CUSTOMER_PRICES = 500;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private ProductUnitConversionRepository conversionRepository;

	@Autowired
	private ProductPriceTierRepository tierRepository;

	@Autowired
	private PromoRepository promoRepository;

	@Autowired
	private ProductCustomerPriceRepository customerPriceRepository;

	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private ProductUnitService productUnitService;

	@Autowired
	private CatalogImageService catalogImageService;

	@Autowired
	private BranchScopeService branchScopeService;

	@Autowired
	private com.saasno.repository.StockRepository stockRepository;

	@Transactional
	public ResponseModel<ProductDTO> create(ProductDTO request) {
		return create(request, PriceHistoryService.MANUAL);
	}

	// source: asal perubahan untuk riwayat harga (MANUAL dari form, IMPORT dari impor berkas).
	@Transactional
	public ResponseModel<ProductDTO> create(ProductDTO request, String source) {
		String name = clean(request.getName());
		if (name == null) {
			throw new ApiException("400", "Nama tidak boleh kosong");
		}
		String unit = clean(request.getUnit());
		if (unit == null) {
			throw new ApiException("400", "Satuan tidak boleh kosong");
		}
		if (request.getPrice() == null) {
			throw new ApiException("400", "Harga tidak boleh kosong");
		}
		validate(name, request.getPrice(), unit);
		BranchScope scope = branchScopeService.current();
		String branchId = branchScopeService.resolveOwner(scope, request.getBranchId());
		Map<String, BigDecimal> conversions = validateConversions(request.getConversions(), unit);
		List<Tier> tiers = validateTiers(request.getPriceTiers(), price(request.getPrice()));
		List<CustomerPrice> customerPrices = validateCustomerPrices(request.getCustomerPrices(), branchId);

		ProductStatus status = request.getStatus() == null || request.getStatus().isBlank() ? ProductStatus.ACTIVE
				: ProductStatus.fromString(request.getStatus().trim());

		ProductEntity entity = new ProductEntity(name, price(request.getPrice()), unit);
		entity.setCostPrice(costPrice(request.getCostPrice()));
		entity.setDescription(description(request.getDescription()));
		entity.setTrackExpiry(Boolean.TRUE.equals(request.getTrackExpiry()));
		entity.setBranchId(branchId);
		entity.setStatus(status.name());
		productRepository.store(entity);
		priceHistoryService.record(entity, null, null, source, sessionUtility.getUserId());
		if (tiers != null) {
			tiers.forEach(t -> tierRepository.store(new ProductPriceTierEntity(entity.getId(), t.minQuantity(), t.price())));
		}
		if (conversions != null) {
			conversions.forEach((toUnit, value) -> conversionRepository
					.store(new ProductUnitConversionEntity(entity.getId(), toUnit, value)));
		}
		if (customerPrices != null) {
			syncCustomerPrices(entity.getId(), List.of(), customerPrices);
		}
		return new ResponseModel<ProductDTO>().success(dto(entity));
	}

	@Transactional(readOnly = true)
	public ResponseEntity<DataResult<ProductDTO>> read(HttpServletRequest request) {
		try {
			return new ResponseEntity<DataResult<ProductDTO>>(find(new DataFilter(request), branchScopeService.current()), HttpStatus.OK);
		} catch (Exception e) {
			Log.error(e.getMessage(), e);
			return new ResponseEntity<DataResult<ProductDTO>>(
					new DataResult<ProductDTO>(BigInteger.ZERO, BigInteger.ZERO), HttpStatus.OK);
		}
	}

	// Dipakai read() dan export download: produk lengkap dengan konversi satuan dan harga bertingkat.
	@Transactional(readOnly = true)
	public DataResult<ProductDTO> find(DataFilter filter, BranchScope scope) {
		DataResult<ProductEntity> result = productRepository.dataTable(scope.applyVisible(filter, "branchId"));
		Set<String> ids = result.getData().stream().map(ProductEntity::getId).collect(Collectors.toSet());
		Map<String, List<ProductUnitConversionEntity>> conversions = conversionRepository.findByProductIds(ids)
				.stream().collect(Collectors.groupingBy(ProductUnitConversionEntity::getProductId));
		Map<String, List<ProductPriceTierEntity>> tiers = tierRepository.findByProductIds(ids)
				.stream().collect(Collectors.groupingBy(ProductPriceTierEntity::getProductId));
		List<ProductCustomerPriceEntity> allPrices = visiblePrices(customerPriceRepository.findByProductIds(ids),
				result.getData(), scope);
		Map<String, List<ProductCustomerPriceEntity>> prices = allPrices.stream()
				.collect(Collectors.groupingBy(ProductCustomerPriceEntity::getProductId));
		Map<String, String> customerNames = customerNames(allPrices);
		Map<String, String> branchNames = branchScopeService
				.names(result.getData().stream().map(ProductEntity::getBranchId).collect(Collectors.toSet()));
		List<ProductDTO> dto = result.getData().stream()
				.map(p -> dto(p, conversions.getOrDefault(p.getId(), List.of()),
						tiers.getOrDefault(p.getId(), List.of()), prices.getOrDefault(p.getId(), List.of()),
						customerNames, branchNames, !scope.contains(p.getBranchId())))
				.collect(Collectors.toList());
		Map<String, Long> versions = productRepository.imageVersions(ids);
		dto.forEach(item -> item.setImageVersion(versions.get(item.getId())));
		return new DataResult<>(dto, result.getTotal(), result.getGrandTotal());
	}

	@Transactional
	public ResponseModel<ProductDTO> update(ProductDTO request) {
		return update(request, PriceHistoryService.MANUAL);
	}

	@Transactional
	public ResponseModel<ProductDTO> update(ProductDTO request, String source) {
		BranchScope scope = branchScopeService.current();
		ProductEntity entity = loadOwned(request.getId(), scope);
		branchScopeService.assertSameOwner(entity.getBranchId(), request.getBranchId());

		// Field yang tidak dikirim (null) dianggap tidak berubah.
		String name = request.getName() == null ? null : clean(request.getName());
		if (request.getName() != null && name == null) {
			throw new ApiException("400", "Nama tidak boleh kosong");
		}
		String unit = request.getUnit() == null ? null : clean(request.getUnit());
		if (request.getUnit() != null && unit == null) {
			throw new ApiException("400", "Satuan tidak boleh kosong");
		}
		validate(name, request.getPrice(), unit);
		ProductStatus status = request.getStatus() == null ? null : ProductStatus.fromString(request.getStatus().trim());

		String finalUnit = unit != null ? unit : entity.getUnit();
		List<ProductUnitConversionEntity> existing = conversionRepository
				.findByProductIds(List.of(entity.getId()));
		Map<String, BigDecimal> conversions = validateConversions(request.getConversions(), finalUnit);
		if (conversions == null && existing.stream().anyMatch(c -> c.getToUnit().equals(finalUnit))) {
			// Satuan produk berubah menjadi satuan yang sudah dipakai sebagai tujuan konversi.
			throw new ApiException("409", "Satuan bentrok dengan konversi satuan yang sudah ada");
		}

		BigDecimal finalPrice = request.getPrice() != null ? price(request.getPrice()) : entity.getPrice();
		List<ProductPriceTierEntity> existingTiers = tierRepository.findByProductIds(List.of(entity.getId()));
		List<Tier> tiers = validateTiers(request.getPriceTiers(), finalPrice);
		List<ProductCustomerPriceEntity> existingPrices = customerPriceRepository.findByProductIds(List.of(entity.getId()));
		List<CustomerPrice> customerPrices = validateCustomerPrices(request.getCustomerPrices(), entity.getBranchId());
		if (tiers == null && !existingTiers.isEmpty() && existingTiers.get(0).getPrice().compareTo(finalPrice) >= 0) {
			// Harga dasar diturunkan sampai tidak lagi lebih mahal dari harga bertingkat yang ada.
			throw new ApiException("400", "Harga harus lebih tinggi dari harga bertingkat");
		}

		BigDecimal priceBefore = entity.getPrice();
		BigDecimal costBefore = entity.getCostPrice();
		if (name != null) {
			entity.setName(name);
		}
		if (request.getPrice() != null) {
			entity.setPrice(price(request.getPrice()));
		}
		if (unit != null) {
			entity.setUnit(unit);
		}
		if (request.getCostPrice() != null) {
			// Harga pokok yang dikirim mengganti yang lama; transaksi yang sudah lewat tidak berubah (dibekukan).
			entity.setCostPrice(costPrice(request.getCostPrice()));
		}
		if (request.getDescription() != null) {
			// Teks kosong menghapus deskripsi; null berarti tidak berubah.
			entity.setDescription(description(request.getDescription()));
		}
		if (status != null) {
			entity.setStatus(status.name());
		}
		if (request.getTrackExpiry() != null && request.getTrackExpiry() != entity.isTrackExpiry()) {
			entity.setTrackExpiry(request.getTrackExpiry());
			if (!request.getTrackExpiry()) {
				// Berhenti melacak: catatan batch tidak lagi dipakai, stok tetap utuh.
				batchService.clear(entity.getId());
			}
		}
		productRepository.update(entity);
		priceHistoryService.record(entity, priceBefore, costBefore, source, sessionUtility.getUserId());

		if (conversions != null) {
			syncConversions(entity.getId(), existing, conversions);
		}
		if (tiers != null) {
			syncTiers(entity.getId(), existingTiers, tiers);
		}
		if (customerPrices != null) {
			syncCustomerPrices(entity.getId(), existingPrices, customerPrices);
		}
		return new ResponseModel<ProductDTO>().success(dto(entity));
	}

	@Transactional
	public ResponseModel<ProductDTO> delete(String id) {
		BranchScope scope = branchScopeService.current();
		ProductEntity entity = loadOwned(id, scope);
		if (stockRepository.countByProduct(id) > 0) {
			throw new ApiException("409", "Produk masih memiliki stok, riwayat stok, atau transaksi");
		}
		if (promoRepository.countByProduct(id) > 0) {
			throw new ApiException("409", "Produk masih dipakai pada promo");
		}
		conversionRepository.deleteByProductId(id);
		tierRepository.deleteByProductId(id);
		customerPriceRepository.deleteByProductId(id);
		productRepository.delete(entity);
		catalogImageService.delete(id);
		return new ResponseModel<ProductDTO>().success();
	}

	// Menyamakan konversi tersimpan dengan daftar yang dikirim: yang ada diubah, yang baru
	// ditambah, yang tidak dikirim dihapus.
	private void syncConversions(String productId, List<ProductUnitConversionEntity> existing,
			Map<String, BigDecimal> requested) {
		Map<String, ProductUnitConversionEntity> byUnit = new HashMap<>();
		existing.forEach(c -> byUnit.put(c.getToUnit(), c));

		for (ProductUnitConversionEntity current : existing) {
			if (!requested.containsKey(current.getToUnit())) {
				conversionRepository.delete(current);
			}
		}
		requested.forEach((toUnit, value) -> {
			ProductUnitConversionEntity current = byUnit.get(toUnit);
			if (current == null) {
				conversionRepository.store(new ProductUnitConversionEntity(productId, toUnit, value));
			} else if (current.getValue().compareTo(value) != 0) {
				current.setValue(value);
				conversionRepository.update(current);
			}
		});
	}

	private ProductDTO dto(ProductEntity entity) {
		List<ProductCustomerPriceEntity> prices = customerPriceRepository.findByProductIds(List.of(entity.getId()));
		ProductDTO dto = dto(entity, conversionRepository.findByProductIds(List.of(entity.getId())),
				tierRepository.findByProductIds(List.of(entity.getId())), prices, customerNames(prices),
				branchScopeService.names(List.of(entity.getBranchId())), false);
		dto.setImageVersion(productRepository.imageVersions(List.of(entity.getId())).get(entity.getId()));
		return dto;
	}

	// Produk milik induk ikut terlihat (hanya baca). Harga khusus untuk customer yang di luar cakupan
	// penonton disembunyikan supaya nama customer cabang lain tidak bocor; harga per tipe tetap tampil.
	private List<ProductCustomerPriceEntity> visiblePrices(List<ProductCustomerPriceEntity> prices,
			List<ProductEntity> products, BranchScope scope) {
		Set<String> inherited = products.stream().filter(p -> !scope.contains(p.getBranchId()))
				.map(ProductEntity::getId).collect(Collectors.toSet());
		if (inherited.isEmpty()) {
			return prices;
		}
		Set<String> customerIds = prices.stream()
				.filter(p -> inherited.contains(p.getProductId()) && p.getCustomerId() != null)
				.map(ProductCustomerPriceEntity::getCustomerId).collect(Collectors.toSet());
		Map<String, String> customerBranch = new HashMap<>();
		customerRepository.findByIds(customerIds).forEach(c -> customerBranch.put(c.getId(), c.getBranchId()));
		return prices.stream()
				.filter(p -> !inherited.contains(p.getProductId()) || !"CUSTOMER".equals(p.getTarget())
						|| scope.contains(customerBranch.get(p.getCustomerId())))
				.collect(Collectors.toList());
	}

	// Produk harus terlihat oleh user; mengubah/menghapus hanya untuk pemilik dan induk-induknya.
	private ProductEntity loadOwned(String id, BranchScope scope) {
		ProductEntity entity = id == null ? null : productRepository.find(id);
		if (entity == null || !scope.canSee(entity.getBranchId())) {
			throw new ApiException("404", "Produk tidak ditemukan");
		}
		if (!scope.contains(entity.getBranchId())) {
			throw new ApiException("403", "Produk milik cabang induk dan hanya dapat dibaca");
		}
		return entity;
	}

	private ProductDTO dto(ProductEntity entity, List<ProductUnitConversionEntity> conversions,
			List<ProductPriceTierEntity> tiers, List<ProductCustomerPriceEntity> prices,
			Map<String, String> customerNames, Map<String, String> branchNames, boolean inherited) {
		ProductDTO dto = new ProductDTO();
		dto.setInherited(inherited);
		dto.setId(entity.getId());
		dto.setBranchId(entity.getBranchId());
		dto.setBranchName(branchNames.get(entity.getBranchId()));
		dto.setSerial(entity.getSerial());
		dto.setName(entity.getName());
		dto.setPrice(entity.getPrice());
		dto.setCostPrice(entity.getCostPrice());
		dto.setDescription(entity.getDescription());
		dto.setTrackExpiry(entity.isTrackExpiry());
		dto.setUnit(entity.getUnit());
		dto.setStatus(entity.getStatus());
		dto.setModificated(entity.getModificated());
		dto.setRegistered(entity.getRegistered());
		dto.setConversions(conversions.stream().map(c -> {
			ProductUnitConversionDTO item = new ProductUnitConversionDTO();
			item.setFromUnit(entity.getUnit());
			item.setToUnit(c.getToUnit());
			item.setValue(c.getValue());
			return item;
		}).collect(Collectors.toList()));
		dto.setCustomerPrices(prices.stream().map(p -> {
			ProductCustomerPriceDTO item = new ProductCustomerPriceDTO();
			item.setTarget(p.getTarget());
			item.setCustomerType(p.getCustomerType());
			item.setCustomerId(p.getCustomerId());
			item.setCustomerName(p.getCustomerId() == null ? null : customerNames.get(p.getCustomerId()));
			item.setPrice(p.getPrice());
			return item;
		}).collect(Collectors.toList()));
		dto.setPriceTiers(tiers.stream().map(t -> {
			ProductPriceTierDTO item = new ProductPriceTierDTO();
			item.setMinQuantity(t.getMinQuantity());
			item.setPrice(t.getPrice());
			return item;
		}).collect(Collectors.toList()));
		return dto;
	}

	private Map<String, String> customerNames(List<ProductCustomerPriceEntity> prices) {
		Set<String> ids = prices.stream().map(ProductCustomerPriceEntity::getCustomerId)
				.filter(id -> id != null).collect(Collectors.toSet());
		Map<String, String> names = new HashMap<>();
		for (CustomerEntity customer : customerRepository.findByIds(ids)) {
			names.put(customer.getId(), customer.getProfile().getName());
		}
		return names;
	}

	// Menyamakan harga khusus tersimpan dengan daftar yang dikirim (dicocokkan lewat sasaran).
	private void syncCustomerPrices(String productId, List<ProductCustomerPriceEntity> existing,
			List<CustomerPrice> requested) {
		Map<String, ProductCustomerPriceEntity> byKey = new HashMap<>();
		existing.forEach(e -> byKey.put(keyOf(e), e));
		Set<String> requestedKeys = requested.stream().map(CustomerPrice::key).collect(Collectors.toSet());

		for (ProductCustomerPriceEntity current : existing) {
			if (!requestedKeys.contains(keyOf(current))) {
				customerPriceRepository.delete(current);
			}
		}
		for (CustomerPrice item : requested) {
			ProductCustomerPriceEntity current = byKey.get(item.key());
			if (current == null) {
				ProductCustomerPriceEntity created = new ProductCustomerPriceEntity();
				created.setProductId(productId);
				created.setTarget(item.target());
				created.setCustomerType(item.customerType());
				created.setCustomerId(item.customerId());
				created.setPrice(item.price());
				customerPriceRepository.store(created);
			} else if (current.getPrice().compareTo(item.price()) != 0) {
				current.setPrice(item.price());
				customerPriceRepository.update(current);
			}
		}
	}

	private String keyOf(ProductCustomerPriceEntity e) {
		return "TYPE".equals(e.getTarget()) ? "TYPE:" + e.getCustomerType() : "CUSTOMER:" + e.getCustomerId();
	}

	// Aturan: sasaran valid dan tidak duplikat; harga rupiah utuh >= 0 (boleh lebih mahal dari harga
	// dasar, tiap sasaran punya daftar harga sendiri). Mengembalikan null kalau tidak dikirim.
	private List<CustomerPrice> validateCustomerPrices(List<ProductCustomerPriceDTO> items, String productBranchId) {
		if (items == null) {
			return null;
		}
		// Customer yang dirujuk harus milik cabang produk ini atau turunannya.
		BranchScope referenceScope = branchScopeService.subtree(productBranchId);
		if (items.size() > MAX_CUSTOMER_PRICES) {
			throw new ApiException("400", "Harga customer tidak boleh lebih dari " + MAX_CUSTOMER_PRICES);
		}
		List<CustomerPrice> result = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (ProductCustomerPriceDTO item : new ArrayList<>(items)) {
			if (item == null) {
				throw new ApiException("400", "Harga customer tidak valid");
			}
			PriceTarget target = PriceTarget.fromString(item.getTarget());
			String customerType = null;
			String customerId = null;
			String key;
			if (target == PriceTarget.TYPE) {
				customerType = CustomerType.fromString(item.getCustomerType()).name();
				key = "TYPE:" + customerType;
			} else {
				customerId = clean(item.getCustomerId());
				if (customerId == null) {
					throw new ApiException("400", "Customer tidak boleh kosong");
				}
				CustomerEntity referenced = customerRepository.find(customerId);
				if (referenced == null || !referenceScope.contains(referenced.getBranchId())) {
					throw new ApiException("404", "Customer tidak ditemukan");
				}
				key = "CUSTOMER:" + customerId;
			}
			if (!seen.add(key)) {
				throw new ApiException("400", "Sasaran harga customer tidak boleh ganda");
			}
			BigDecimal price = item.getPrice();
			if (price == null || price.signum() < 0 || price.stripTrailingZeros().scale() > 0
					|| price(price).compareTo(MAX_PRICE) > 0) {
				throw new ApiException("400", "Harga customer tidak valid");
			}
			result.add(new CustomerPrice(key, target.name(), customerType, customerId, price(price)));
		}
		return result;
	}

	// Harga akhir satu produk untuk customer (opsional) dan jumlah tertentu. Urutan: harga customer
	// tertentu, lalu harga tipe customer, lalu harga dasar; harga bertingkat dipakai kalau lebih murah.
	@Transactional(readOnly = true)
	public ResponseModel<ProductPriceResultDTO> price(String productId, String customerId, BigDecimal quantity) {
		BranchScope scope = branchScopeService.current();
		ProductEntity product = productRepository.find(productId);
		if (product == null || !scope.canSee(product.getBranchId())) {
			throw new ApiException("404", "Produk tidak ditemukan");
		}
		if ("DISABLED".equals(product.getStatus())) {
			throw new ApiException("409", "Produk nonaktif");
		}
		BigDecimal qty = quantity == null ? BigDecimal.ONE : quantity;
		if (qty.signum() <= 0 || qty.stripTrailingZeros().scale() > CONVERSION_SCALE
				|| qty.setScale(CONVERSION_SCALE, RoundingMode.UNNECESSARY).compareTo(MAX_CONVERSION) > 0) {
			throw new ApiException("400", "Jumlah tidak valid");
		}

		String cleanedCustomer = clean(customerId);
		CustomerEntity customer = null;
		if (cleanedCustomer != null) {
			customer = customerRepository.find(cleanedCustomer);
			if (customer == null || !scope.contains(customer.getBranchId())) {
				throw new ApiException("404", "Customer tidak ditemukan");
			}
		}

		return new ResponseModel<ProductPriceResultDTO>().success(priceFor(product, customer, qty));
	}

	// Harga akhir satu produk untuk customer (boleh null) dan jumlah (satuan dasar). Tanpa pengecekan akses:
	// pemanggil harus sudah memastikan produk dan customer boleh dipakai. Dipakai juga oleh kasir.
	@Transactional(readOnly = true)
	public ProductPriceResultDTO priceFor(ProductEntity product, CustomerEntity customer, BigDecimal qty) {
		String productId = product.getId();
		String cleanedCustomer = customer == null ? null : customer.getId();
		BigDecimal unitPrice = product.getPrice();
		BigDecimal special = null;
		String source = "BASE";
		if (customer != null) {
			List<ProductCustomerPriceEntity> rules = customerPriceRepository.findByProductIds(List.of(productId));
			for (ProductCustomerPriceEntity rule : rules) {
				if ("CUSTOMER".equals(rule.getTarget()) && customer.getId().equals(rule.getCustomerId())) {
					special = rule.getPrice();
					source = "CUSTOMER";
					break;
				}
			}
			if (special == null) {
				for (ProductCustomerPriceEntity rule : rules) {
					if ("TYPE".equals(rule.getTarget()) && customer.getType().equals(rule.getCustomerType())) {
						special = rule.getPrice();
						source = "CUSTOMER_TYPE";
						break;
					}
				}
			}
			if (special != null) {
				unitPrice = special;
			}
		}

		// Harga bertingkat hanya dipakai kalau lebih murah dari harga dasar/khusus. Tingkat diurutkan naik
		// berdasarkan minQuantity, jadi tingkat tertinggi yang terpenuhi yang terakhir menang.
		BigDecimal tierMin = null;
		for (ProductPriceTierEntity tier : tierRepository.findByProductIds(List.of(productId))) {
			if (tier.getMinQuantity().compareTo(qty) <= 0 && tier.getPrice().compareTo(unitPrice) < 0) {
				unitPrice = tier.getPrice();
				tierMin = tier.getMinQuantity();
			}
		}

		ProductPriceResultDTO result = new ProductPriceResultDTO();
		result.setProductId(productId);
		result.setCustomerId(cleanedCustomer);
		result.setQuantity(qty);
		result.setBasePrice(product.getPrice());
		result.setPriceSource(source);
		result.setSpecialPrice(special);
		result.setTierMinQuantity(tierMin);
		result.setUnitPrice(unitPrice);
		result.setTotal(unitPrice.multiply(qty).setScale(2, RoundingMode.HALF_UP));
		return result;
	}

	// Menyamakan harga bertingkat tersimpan dengan daftar yang dikirim (dicocokkan lewat minQuantity).
	private void syncTiers(String productId, List<ProductPriceTierEntity> existing, List<Tier> requested) {
		Map<BigDecimal, ProductPriceTierEntity> byQuantity = new HashMap<>();
		existing.forEach(t -> byQuantity.put(t.getMinQuantity().setScale(CONVERSION_SCALE), t));
		Set<BigDecimal> requestedQuantities = requested.stream().map(Tier::minQuantity).collect(Collectors.toSet());

		for (ProductPriceTierEntity current : existing) {
			if (!requestedQuantities.contains(current.getMinQuantity().setScale(CONVERSION_SCALE))) {
				tierRepository.delete(current);
			}
		}
		for (Tier tier : requested) {
			ProductPriceTierEntity current = byQuantity.get(tier.minQuantity());
			if (current == null) {
				tierRepository.store(new ProductPriceTierEntity(productId, tier.minQuantity(), tier.price()));
			} else if (current.getPrice().compareTo(tier.price()) != 0) {
				current.setPrice(tier.price());
				tierRepository.update(current);
			}
		}
	}

	// Aturan: minQuantity > 0 dan unik; harga bulat > 0; makin banyak dibeli harga harus makin murah,
	// dimulai dari harga dasar. Mengembalikan null kalau tingkat harga tidak dikirim.
	private List<Tier> validateTiers(List<ProductPriceTierDTO> items, BigDecimal basePrice) {
		if (items == null) {
			return null;
		}
		List<Tier> tiers = new ArrayList<>();
		Set<BigDecimal> seen = new HashSet<>();
		for (ProductPriceTierDTO item : new ArrayList<>(items)) {
			if (item == null) {
				throw new ApiException("400", "Harga bertingkat tidak valid");
			}
			BigDecimal quantity = item.getMinQuantity();
			if (quantity == null || quantity.signum() <= 0 || quantity.stripTrailingZeros().scale() > CONVERSION_SCALE
					|| quantity.setScale(CONVERSION_SCALE, RoundingMode.UNNECESSARY).compareTo(MAX_CONVERSION) > 0) {
				throw new ApiException("400", "Jumlah minimum harga bertingkat tidak valid");
			}
			BigDecimal price = item.getPrice();
			if (price == null || price.signum() <= 0 || price.stripTrailingZeros().scale() > 0
					|| price(price).compareTo(MAX_PRICE) > 0) {
				throw new ApiException("400", "Harga bertingkat tidak valid");
			}
			BigDecimal normalizedQuantity = quantity.setScale(CONVERSION_SCALE, RoundingMode.UNNECESSARY);
			if (!seen.add(normalizedQuantity)) {
				throw new ApiException("400", "Jumlah minimum harga bertingkat tidak boleh ganda");
			}
			tiers.add(new Tier(normalizedQuantity, price(price)));
		}
		tiers.sort(Comparator.comparing(Tier::minQuantity));

		BigDecimal previous = basePrice;
		for (Tier tier : tiers) {
			if (tier.price().compareTo(previous) >= 0) {
				throw new ApiException("400", "Harga bertingkat harus lebih rendah dari harga untuk jumlah yang lebih kecil");
			}
			previous = tier.price();
		}
		return tiers;
	}

	// Mengembalikan toUnit -> value (urutan input terjaga), atau null kalau konversi tidak dikirim.
	private Map<String, BigDecimal> validateConversions(List<ProductUnitConversionDTO> items, String productUnit) {
		if (items == null) {
			return null;
		}
		Map<String, BigDecimal> result = new LinkedHashMap<>();
		Set<String> seen = new HashSet<>(List.of(productUnit));
		for (ProductUnitConversionDTO item : new ArrayList<>(items)) {
			if (item == null) {
				throw new ApiException("400", "Konversi satuan tidak valid");
			}
			// fromUnit dari client diabaikan, tetapi kalau dikirim harus sama dengan satuan produk.
			String from = clean(item.getFromUnit());
			if (from != null && !from.equals(productUnit)) {
				throw new ApiException("400", "Satuan asal konversi harus satuan produk");
			}
			String toUnit = clean(item.getToUnit());
			if (toUnit == null || !UNIT.matcher(toUnit).matches()) {
				throw new ApiException("400", "Satuan konversi tidak valid");
			}
			if (toUnit.equals(productUnit)) {
				throw new ApiException("400", "Satuan konversi harus berbeda dari satuan produk");
			}
			productUnitService.assertExists(toUnit);
			if (!seen.add(toUnit)) {
				throw new ApiException("400", "Satuan konversi tidak boleh ganda: " + toUnit);
			}
			BigDecimal value = item.getValue();
			if (value == null || value.signum() <= 0 || value.stripTrailingZeros().scale() > CONVERSION_SCALE
					|| value.setScale(CONVERSION_SCALE, RoundingMode.UNNECESSARY).compareTo(MAX_CONVERSION) > 0) {
				throw new ApiException("400", "Nilai konversi tidak valid");
			}
			result.put(toUnit, value.setScale(CONVERSION_SCALE, RoundingMode.UNNECESSARY));
		}
		return result;
	}

	// Argumen null berarti tidak dikirim, jadi tidak divalidasi.
	private void validate(String name, BigDecimal price, String unit) {
		if (name != null && name.length() > 256) {
			throw new ApiException("400", "Nama tidak boleh lebih dari 256 karakter");
		}
		if (price != null && (price.signum() < 0 || price(price).compareTo(MAX_PRICE) > 0)) {
			throw new ApiException("400", "Harga tidak valid");
		}
		// Harga dalam rupiah utuh; input di frontend juga hanya menerima angka bulat.
		if (price != null && price.stripTrailingZeros().scale() > 0) {
			throw new ApiException("400", "Harga harus bilangan bulat");
		}
		if (unit != null) {
			if (!UNIT.matcher(unit).matches()) {
				throw new ApiException("400", "Satuan tidak valid");
			}
			productUnitService.assertExists(unit);
		}
	}

	// Deskripsi opsional, maksimal 1000 karakter; kosong disimpan sebagai null.
	private String description(String value) {
		String text = value == null ? null : value.strip();
		if (text == null || text.isEmpty()) {
			return null;
		}
		if (text.length() > 1000) {
			throw new ApiException("400", "Deskripsi tidak boleh lebih dari 1000 karakter");
		}
		return text;
	}

	// Harga pokok opsional: rupiah utuh >= 0; kosong (null) berarti belum diketahui.
	private BigDecimal costPrice(BigDecimal value) {
		if (value == null) {
			return null;
		}
		if (value.signum() < 0 || value.stripTrailingZeros().scale() > 0 || price(value).compareTo(MAX_PRICE) > 0) {
			throw new ApiException("400", "Harga pokok tidak valid");
		}
		return price(value);
	}

	private BigDecimal price(BigDecimal value) {
		return value.setScale(2, RoundingMode.HALF_UP);
	}

	private String clean(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

}
