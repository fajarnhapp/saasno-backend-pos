package com.saasno.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasno.entity.BranchEntity;
import com.saasno.entity.PettyCashEntryEntity;
import com.saasno.entity.ProductEntity;
import com.saasno.entity.StockMovementEntity;
import com.saasno.entity.StoreExpenseEntity;
import com.saasno.repository.ConfigurationRepository;
import com.saasno.repository.ExpenseRepository;
import com.saasno.repository.PettyCashRepository;
import com.saasno.repository.ProductRepository;
import com.saasno.repository.UserRepository;
import com.saasno.utility.BranchScope;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.constant.ConfigurationKey;
import com.saasno.utility.dto.finance.ExpenseDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.logger.Log;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;

// Biaya operasional toko. Dua jenis: CASH (beli perlengkapan dengan uang kas kecil) dan STOCK_USE (memakai stok sendiri;
// nilainya jumlah x harga pokok, tidak menyentuh kas kecil). Biaya dicatat di cabang tempat user berada, tidak dihapus
// (hanya dibatalkan dengan alasan), dan dipisah dari penjualan supaya laporan kas penjualan tidak ikut minus. Saldo kas
// kecil boleh minus dan biaya di atas batas peringatan tetap diterima; keduanya hanya diberi PERINGATAN (persetujuan
// dilakukan lisan, nama pemberinya boleh dicatat sebagai keterangan).
@Service
public class ExpenseService {

	private static final Pattern CODE = Pattern.compile("^[A-Z0-9_]{1,32}$");
	private static final int MAX_DESCRIPTION = 256;
	private static final int MAX_RECEIPT = 64;
	private static final int MAX_REASON = 500;
	private static final int MAX_APPROVED_BY = 128;
	private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999999.99");
	private static final BigDecimal DEFAULT_WARNING = new BigDecimal("500000");
	private static final String USE_REFERENCE = "STOCK_USE";
	private static final String USE_VOID_REFERENCE = "STOCK_USE_VOID";
	private static final String DEFAULT_CATEGORIES = "[{\"value\":\"SUPPLIES\",\"label\":\"Perlengkapan\"},{\"value\":\"CLEANING\",\"label\":\"Kebersihan\"},"
			+ "{\"value\":\"UTILITY\",\"label\":\"Listrik/Air\"},{\"value\":\"TRANSPORT\",\"label\":\"Transportasi\"},"
			+ "{\"value\":\"REPAIR\",\"label\":\"Perbaikan\"},{\"value\":\"OTHER\",\"label\":\"Lainnya\"}]";

	@Autowired
	private ExpenseRepository expenseRepository;

	@Autowired
	private PettyCashRepository pettyCashRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ConfigurationRepository configurationRepository;

	@Autowired
	private StockService stockService;

	@Autowired
	private NotificationPublisher notificationPublisher;

	@Autowired
	private NotificationRecipients notificationRecipients;

	@Autowired
	private BranchScopeService branchScopeService;

	@Autowired
	private SessionUtility sessionUtility;

	@Autowired
	private ObjectMapper objectMapper;

	// ---------------------------------------------------------------- catat

	// Beli perlengkapan dengan uang kas kecil.
	@Transactional
	public ResponseModel<ExpenseDTO> createCash(ExpenseDTO request) {
		BranchScope scope = branchScopeService.current();
		BranchEntity branch = branchScopeService.ownBranch(scope, request.getBranchId(), "Biaya hanya dapat dicatat di cabang Anda sendiri");
		String category = category(request.getCategory());
		String description = required(request.getDescription(), "Deskripsi", MAX_DESCRIPTION);
		String reason = required(request.getReason(), "Alasan", MAX_REASON);
		BigDecimal amount = rupiah(request.getAmount(), "Nominal");
		if (amount.signum() <= 0) {
			throw new ApiException("400", "Nominal harus lebih dari 0");
		}
		BigDecimal threshold = warningAmount();
		List<String> warnings = new ArrayList<>();

		StoreExpenseEntity expense = base(branch, StoreExpenseEntity.CASH, category, description, amount, reason, request);
		expense.setOverThreshold(amount.compareTo(threshold) > 0);
		expenseRepository.store(expense, branch.getSerial());
		expenseRepository.getEntityManager().flush();

		// Uang keluar dari kas kecil; saldo boleh minus, hanya diberi peringatan.
		BigDecimal balance = pettyCashRepository.adjust(branch.getId(), amount.negate());
		entry(branch.getId(), PettyCashEntryEntity.EXPENSE, amount.negate(), balance, expense.getId(), description);
		notifyOverLimit(expense, branch, threshold);
		// Hanya saat saldo BARU menyeberang ke minus; saldo yang sudah minus tidak diulang tiap biaya.
		if (balance.signum() < 0 && balance.add(amount).signum() >= 0) {
			notifyNegativeBalance(branch, balance);
		}
		if (expense.isOverThreshold()) {
			warnings.add("Biaya melebihi batas peringatan Rp " + threshold.toPlainString() + ". Pastikan persetujuan lisan sudah didapat.");
		}
		if (balance.signum() < 0) {
			warnings.add("Saldo kas kecil menjadi minus (Rp " + balance.toPlainString() + "). Segera isi kas kecil.");
		}
		return result(expense, warnings, balance);
	}

	// Memakai stok sendiri (contoh, konsumsi, habis pakai). Stok berkurang dengan jenis mutasi USE dan nilai biayanya
	// jumlah x harga pokok; kas kecil tidak tersentuh.
	@Transactional
	public ResponseModel<ExpenseDTO> useStock(ExpenseDTO request) {
		BranchScope scope = branchScopeService.current();
		BranchEntity branch = branchScopeService.ownBranch(scope, request.getBranchId(), "Stok hanya dapat dipakai di cabang Anda sendiri");
		String category = category(request.getCategory());
		String reason = required(request.getReason(), "Alasan", MAX_REASON);
		ProductEntity product = stockService.visibleProduct(branch.getId(), request.getProductId());
		BigDecimal quantity = stockService.toBase(product, request.getQuantity(), request.getUnit(), false);
		String description = request.getDescription() == null || request.getDescription().isBlank()
				? "Pemakaian " + product.getName() : required(request.getDescription(), "Deskripsi", MAX_DESCRIPTION);

		List<String> warnings = new ArrayList<>();
		BigDecimal unitCost = product.getCostPrice();
		BigDecimal amount = unitCost == null ? BigDecimal.ZERO.setScale(2)
				: unitCost.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
		if (unitCost == null) {
			warnings.add("Produk belum punya harga pokok, nilai biaya dicatat Rp 0. Isi harga pokok di menu Produk.");
		}
		BigDecimal threshold = warningAmount();

		StoreExpenseEntity expense = base(branch, StoreExpenseEntity.STOCK_USE, category, description, amount, reason, request);
		expense.setProductId(product.getId());
		expense.setQuantity(quantity);
		expense.setUnitCost(unitCost);
		expense.setOverThreshold(amount.compareTo(threshold) > 0);
		expenseRepository.store(expense, branch.getSerial());
		expenseRepository.getEntityManager().flush();

		stockService.applyMovement(branch.getId(), product, quantity.negate(), StockMovementEntity.USE, USE_REFERENCE,
				expense.getId(), reason, sessionUtility.getUserId(), unitCost);
		notifyOverLimit(expense, branch, threshold);
		if (expense.isOverThreshold()) {
			warnings.add("Nilai pemakaian melebihi batas peringatan Rp " + threshold.toPlainString() + ". Pastikan persetujuan lisan sudah didapat.");
		}
		return result(expense, warnings, null);
	}

	// Yang berhak membaca laporan keuangan di cabang itu (selain pencatat) diberi tahu ada biaya di atas batas.
	private void notifyOverLimit(StoreExpenseEntity expense, BranchEntity branch, BigDecimal threshold) {
		if (!expense.isOverThreshold()) {
			return;
		}
		Set<String> recipients = notificationRecipients.withApi("GET_API_FINANCE_REPORT", branch.getId());
		recipients.remove(sessionUtility.getUserId());
		notificationPublisher.notify(recipients, "EXPENSE_OVER_LIMIT", "Biaya melebihi batas peringatan",
				expense.getSerial() + " di " + branch.getName() + " senilai " + notificationRecipients.rupiah(expense.getAmount())
						+ " (batas " + notificationRecipients.rupiah(threshold) + "), dicatat oleh "
						+ notificationRecipients.username(sessionUtility.getUserId()) + ".",
				"/finance/expense");
	}

	// Yang berhak mengisi kas kecil di cabang itu (selain pencatat) diberi tahu saldo menjadi minus.
	private void notifyNegativeBalance(BranchEntity branch, BigDecimal balance) {
		Set<String> recipients = notificationRecipients.withApi("POST_API_PETTY_CASH_TOPUP", branch.getId());
		recipients.remove(sessionUtility.getUserId());
		notificationPublisher.notify(recipients, "PETTY_CASH_NEGATIVE", "Saldo kas kecil minus",
				"Kas kecil " + branch.getName() + " menjadi " + notificationRecipients.rupiah(balance) + ". Segera isi kas kecil.",
				"/finance/petty-cash");
	}

	// ---------------------------------------------------------------- baca

	// Biaya di cabang user dan turunannya (hanya baca bagi induk), yang terbaru dulu.
	@Transactional(readOnly = true)
	public ResponseEntity<DataResult<ExpenseDTO>> read(HttpServletRequest request) {
		try {
			return new ResponseEntity<>(find(new DataFilter(request), branchScopeService.current()), HttpStatus.OK);
		} catch (ApiException e) {
			throw e;
		} catch (Exception e) {
			Log.error(e.getMessage(), e);
			return new ResponseEntity<>(new DataResult<ExpenseDTO>(BigInteger.ZERO, BigInteger.ZERO), HttpStatus.OK);
		}
	}

	// Isi read untuk satu filter dan cakupan cabang (dipakai juga oleh pengunduhan).
	@Transactional(readOnly = true)
	public DataResult<ExpenseDTO> find(DataFilter requested, BranchScope scope) {
		DataFilter filter = scope.apply(requested, "branchId");
		if (filter.getOrder() == null) {
			filter.setOrder("registered");
			filter.setDescending(true);
		}
		DataResult<StoreExpenseEntity> result = expenseRepository.dataTable(filter);
		return new DataResult<>(dtos(result.getData()), result.getTotal(), result.getGrandTotal());
	}

	// ---------------------------------------------------------------- batalkan

	// Membatalkan catatan (alasan wajib), hanya di cabang pencatat. Biaya kas mengembalikan uang ke kas kecil; pemakaian
	// stok mengembalikan stoknya. Catatan asli tetap ada (status VOIDED) dan ledger mencatat pembaliknya.
	@Transactional
	public ResponseModel<ExpenseDTO> voidExpense(String id, String reason) {
		BranchScope scope = branchScopeService.current();
		StoreExpenseEntity expense = id == null ? null : expenseRepository.findLock(id.trim());
		if (expense == null || (!scope.isGlobal() && !expense.getBranchId().equals(scope.getBranchId()))) {
			throw new ApiException("404", "Catatan biaya tidak ditemukan");
		}
		String text = required(reason, "Alasan", MAX_REASON);
		if (!StoreExpenseEntity.RECORDED.equals(expense.getStatus())) {
			throw new ApiException("409", "Catatan biaya sudah " + expense.getStatus());
		}
		String userId = sessionUtility.getUserId();
		BigDecimal balance = null;
		if (StoreExpenseEntity.CASH.equals(expense.getKind())) {
			balance = pettyCashRepository.adjust(expense.getBranchId(), expense.getAmount());
			entry(expense.getBranchId(), PettyCashEntryEntity.EXPENSE_VOID, expense.getAmount(), balance, expense.getId(),
					"Pembatalan " + expense.getSerial());
		} else {
			ProductEntity product = productRepository.find(expense.getProductId());
			stockService.applyMovement(expense.getBranchId(), product, expense.getQuantity(), StockMovementEntity.RETURN,
					USE_VOID_REFERENCE, expense.getId(), "Pembatalan " + expense.getSerial(), userId, expense.getUnitCost());
		}
		expense.setStatus(StoreExpenseEntity.VOIDED);
		expense.setVoidedBy(userId);
		expense.setVoidedAt(LocalDateTime.now());
		expense.setVoidReason(text);
		expenseRepository.update(expense);
		return result(expense, List.of(), balance);
	}

	// ---------------------------------------------------------------- kategori & batas (pengaturan)

	public record Category(String value, String label) {
	}

	public List<Category> categories() {
		String json = configurationRepository.get(ConfigurationKey.EXPENSE_CATEGORY);
		try {
			return objectMapper.readValue(json == null ? DEFAULT_CATEGORIES : json, new TypeReference<List<Category>>() {
			});
		} catch (JsonProcessingException e) {
			Log.error("EXPENSE_CATEGORY is unreadable, falling back to the defaults", e);
			try {
				return objectMapper.readValue(DEFAULT_CATEGORIES, new TypeReference<List<Category>>() {
				});
			} catch (JsonProcessingException ignored) {
				return List.of();
			}
		}
	}

	// Dipakai ConfigurationService: daftar kategori harus JSON [{value,label}], kode unik berbentuk A-Z0-9_.
	public String validateCategories(String raw) {
		List<Category> list;
		try {
			list = objectMapper.readValue(raw == null ? "" : raw, new TypeReference<List<Category>>() {
			});
		} catch (JsonProcessingException e) {
			throw new ApiException("400", "EXPENSE_CATEGORY harus berupa array JSON {value, label}");
		}
		if (list == null || list.isEmpty()) {
			throw new ApiException("400", "EXPENSE_CATEGORY tidak boleh kosong");
		}
		Set<String> seen = new HashSet<>();
		for (Category category : list) {
			if (category == null || category.value() == null || !CODE.matcher(category.value()).matches()) {
				throw new ApiException("400", "Kode kategori biaya hanya boleh A-Z, 0-9, atau _ (maksimal 32 karakter)");
			}
			if (category.label() == null || category.label().isBlank() || category.label().length() > 64) {
				throw new ApiException("400", "Label kategori biaya tidak boleh kosong (maksimal 64 karakter)");
			}
			if (!seen.add(category.value())) {
				throw new ApiException("400", "Kategori biaya tidak boleh ganda: " + category.value());
			}
		}
		try {
			return objectMapper.writeValueAsString(list);
		} catch (JsonProcessingException e) {
			throw new ApiException("500", "EXPENSE_CATEGORY tidak dapat disimpan");
		}
	}

	// Untuk formulir: kategori yang berlaku dan batas peringatan.
	@Transactional(readOnly = true)
	public ResponseModel<Map<String, Object>> options() {
		branchScopeService.current();
		Map<String, Object> options = new HashMap<>();
		options.put("categories", categories());
		options.put("warningAmount", warningAmount());
		return new ResponseModel<Map<String, Object>>().success(options);
	}

	// Batas peringatan biaya (rupiah); biaya di atasnya tetap diterima tetapi diberi peringatan.
	public BigDecimal warningAmount() {
		String value = configurationRepository.get(ConfigurationKey.EXPENSE_WARNING_AMOUNT);
		try {
			return value == null ? DEFAULT_WARNING : new BigDecimal(value.trim());
		} catch (NumberFormatException e) {
			return DEFAULT_WARNING;
		}
	}

	// ---------------------------------------------------------------- bantu

	private StoreExpenseEntity base(BranchEntity branch, String kind, String category, String description, BigDecimal amount,
			String reason, ExpenseDTO request) {
		StoreExpenseEntity expense = new StoreExpenseEntity();
		expense.setBranchId(branch.getId());
		expense.setKind(kind);
		expense.setCategory(category);
		expense.setDescription(description);
		expense.setAmount(amount);
		expense.setReason(reason);
		expense.setReceiptNo(optional(request.getReceiptNo(), "Receipt number", MAX_RECEIPT));
		expense.setApprovedBy(optional(request.getApprovedBy(), "Approved by", MAX_APPROVED_BY));
		expense.setCreatedBy(sessionUtility.getUserId());
		return expense;
	}

	private void entry(String branchId, String type, BigDecimal amount, BigDecimal balanceAfter, String expenseId, String note) {
		PettyCashEntryEntity entry = new PettyCashEntryEntity();
		entry.setBranchId(branchId);
		entry.setType(type);
		entry.setAmount(amount);
		entry.setBalanceAfter(balanceAfter);
		entry.setExpenseId(expenseId);
		entry.setNote(note.length() > 500 ? note.substring(0, 500) : note);
		entry.setCreatedBy(sessionUtility.getUserId());
		pettyCashRepository.addEntry(entry);
	}

	private ResponseModel<ExpenseDTO> result(StoreExpenseEntity expense, List<String> warnings, BigDecimal balance) {
		ExpenseDTO dto = dtos(List.of(expense)).get(0);
		dto.setWarnings(warnings);
		dto.setPettyCashBalance(balance);
		return new ResponseModel<ExpenseDTO>().success(dto);
	}

	private String category(String value) {
		String code = value == null ? null : value.trim();
		if (code == null || categories().stream().noneMatch(c -> c.value().equals(code))) {
			throw new ApiException("400", "Kategori tidak valid");
		}
		return code;
	}

	private String required(String value, String field, int max) {
		String text = value == null ? null : value.trim();
		if (text == null || text.isEmpty()) {
			throw new ApiException("400", field + " tidak boleh kosong");
		}
		if (text.length() > max) {
			throw new ApiException("400", field + " tidak boleh lebih dari " + max + " karakter");
		}
		return text;
	}

	private String optional(String value, String field, int max) {
		String text = value == null ? null : value.trim();
		if (text == null || text.isEmpty()) {
			return null;
		}
		if (text.length() > max) {
			throw new ApiException("400", field + " tidak boleh lebih dari " + max + " karakter");
		}
		return text;
	}

	// Rupiah utuh >= 0.
	private BigDecimal rupiah(BigDecimal value, String field) {
		if (value == null || value.signum() < 0 || value.stripTrailingZeros().scale() > 0
				|| value.setScale(2, RoundingMode.UNNECESSARY).compareTo(MAX_AMOUNT) > 0) {
			throw new ApiException("400", field + " tidak valid");
		}
		return value.setScale(2, RoundingMode.UNNECESSARY);
	}

	private List<ExpenseDTO> dtos(List<StoreExpenseEntity> expenses) {
		Map<String, String> branchNames = branchScopeService
				.names(expenses.stream().map(StoreExpenseEntity::getBranchId).collect(Collectors.toSet()));
		Set<String> userIds = new HashSet<>();
		expenses.forEach(e -> {
			userIds.add(e.getCreatedBy());
			if (e.getVoidedBy() != null) {
				userIds.add(e.getVoidedBy());
			}
		});
		Map<String, String> usernames = userRepository.findUsernames(userIds);
		Map<String, ProductEntity> products = new HashMap<>();
		productRepository.findByIds(expenses.stream().map(StoreExpenseEntity::getProductId).filter(p -> p != null)
				.collect(Collectors.toSet())).forEach(p -> products.put(p.getId(), p));

		return expenses.stream().map(e -> {
			ExpenseDTO dto = new ExpenseDTO();
			dto.setId(e.getId());
			dto.setSerial(e.getSerial());
			dto.setBranchId(e.getBranchId());
			dto.setBranchName(branchNames.get(e.getBranchId()));
			dto.setKind(e.getKind());
			dto.setCategory(e.getCategory());
			dto.setDescription(e.getDescription());
			dto.setAmount(e.getAmount());
			dto.setReceiptNo(e.getReceiptNo());
			dto.setReason(e.getReason());
			dto.setApprovedBy(e.getApprovedBy());
			ProductEntity product = e.getProductId() == null ? null : products.get(e.getProductId());
			dto.setProductId(e.getProductId());
			dto.setProductSerial(product == null ? null : product.getSerial());
			dto.setProductName(product == null ? null : product.getName());
			dto.setUnit(product == null ? null : product.getUnit());
			dto.setQuantity(e.getQuantity());
			dto.setUnitCost(e.getUnitCost());
			dto.setOverThreshold(e.isOverThreshold());
			dto.setStatus(e.getStatus());
			dto.setCreatedBy(e.getCreatedBy());
			dto.setCreatedByName(usernames.get(e.getCreatedBy()));
			dto.setRegistered(e.getRegistered());
			dto.setVoidedBy(e.getVoidedBy());
			dto.setVoidedByName(e.getVoidedBy() == null ? null : usernames.get(e.getVoidedBy()));
			dto.setVoidedAt(e.getVoidedAt());
			dto.setVoidReason(e.getVoidReason());
			return dto;
		}).collect(Collectors.toList());
	}

}
