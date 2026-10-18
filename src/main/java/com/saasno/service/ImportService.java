package com.saasno.service;

import static com.saasno.service.AnalyticsSupport.str;
import static com.saasno.service.SqlHelper.clean;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.saasno.entity.ProductEntity;
import com.saasno.entity.StockMovementEntity;
import com.saasno.utility.BranchScope;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.dto.dataimport.ImportResultDTO;
import com.saasno.utility.dto.product.ProductDTO;
import com.saasno.utility.dto.product.ProductUnitDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.logger.Log;
import com.saasno.utility.model.ResponseModel;

// Impor produk, harga massal, dan stok awal dari berkas .xlsx atau .csv. Semua baris diterapkan lewat layanan yang sama dengan form
// (jadi semua aturan validasi dan riwayat harga ikut berlaku) di dalam satu transaksi: pemeriksaan (dryRun) selalu dibatalkan,
// dan impor sungguhan juga dibatalkan seluruhnya bila ada satu baris yang galat.
@Service
public class ImportService {

	private static final int MAX_ROWS = 2000;
	private static final int PREVIEW_ROWS = 20;
	private static final long MAX_BYTES = 5L * 1024 * 1024;

	public enum Type {
		PRODUCT(List.of("serial", "nama", "satuan", "harga_jual", "harga_pokok", "status", "deskripsi", "cabang"),
				List.of("(kosong = produk baru)", "Beras 5 kg", "PCS", "72000", "62000", "ACTIVE", "Beras pulen", "(serial cabang, kosong = cabang Anda)"),
				"Kosongkan serial untuk produk baru (nama, satuan, harga_jual wajib). Isi serial untuk mengubah produk yang ada: hanya kolom yang diisi yang diubah."),
		PRICE(List.of("serial", "harga_jual", "harga_pokok"), List.of("10202608001", "72000", "62000"),
				"Ubah harga produk yang sudah ada. serial wajib; isi harga_jual dan/atau harga_pokok."),
		STOCK(List.of("cabang", "serial", "jumlah", "satuan", "harga_beli", "catatan", "kadaluwarsa", "batch"),
				List.of("10202608002", "10202608001", "50", "(kosong = satuan dasar)", "62000", "Stok awal", "(yyyy-mm-dd, wajib untuk produk berkedaluwarsa)", "(opsional)"),
				"Menambah stok (mutasi masuk) per cabang. cabang = serial cabang, serial = serial produk. harga_beli opsional (per satuan yang diisi di kolom satuan). kadaluwarsa (yyyy-mm-dd) dan batch hanya untuk produk yang dilacak kedaluwarsanya.");

		final List<String> headers;
		final List<String> example;
		final String help;

		Type(List<String> headers, List<String> example, String help) {
			this.headers = headers;
			this.example = example;
			this.help = help;
		}
	}

	@Autowired
	private SqlHelper sql;

	@Autowired
	private ProductService productService;

	@Autowired
	private ProductUnitService productUnitService;

	@Autowired
	private StockService stockService;

	@Autowired
	private BranchScopeService branchScopeService;

	@Autowired
	private PermissionService permissionService;

	@Autowired
	private BatchService batchService;

	@Autowired
	private SessionUtility sessionUtility;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private record Outcome(boolean created, String summary) {
	}

	// ------------------------------------------------------------------ templat

	public byte[] template(String typeName) {
		Type type = type(typeName);
		assertAllowed(type);
		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet data = workbook.createSheet("Data");
			CellStyle bold = workbook.createCellStyle();
			Font font = workbook.createFont();
			font.setBold(true);
			bold.setFont(font);
			Row header = data.createRow(0);
			Row example = data.createRow(1);
			for (int i = 0; i < type.headers.size(); i++) {
				Cell h = header.createCell(i);
				h.setCellValue(type.headers.get(i));
				h.setCellStyle(bold);
				example.createCell(i).setCellValue(type.example.get(i));
				data.setColumnWidth(i, 22 * 256);
			}
			Sheet help = workbook.createSheet("Petunjuk");
			help.createRow(0).createCell(0).setCellValue(type.help);
			help.createRow(2).createCell(0).setCellValue("Hapus baris contoh sebelum mengunggah. Angka ditulis polos tanpa pemisah ribuan. Maksimal " + MAX_ROWS + " baris.");
			help.setColumnWidth(0, 120 * 256);
			workbook.write(out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new ApiException("500", "Templat tidak dapat dibuat");
		}
	}

	// ------------------------------------------------------------------ impor

	public ResponseModel<ImportResultDTO> process(String typeName, MultipartFile file, boolean commit) {
		Type type = type(typeName);
		assertAllowed(type);
		if (file == null || file.isEmpty()) {
			throw new ApiException("400", "Berkas tidak boleh kosong");
		}
		if (file.getSize() > MAX_BYTES) {
			throw new ApiException("400", "Ukuran berkas maksimal 5 MB");
		}
		List<Map<String, String>> rows = parse(file);
		if (rows.isEmpty()) {
			throw new ApiException("400", "Berkas tidak berisi baris data");
		}
		if (rows.size() > MAX_ROWS) {
			throw new ApiException("400", "Maksimal " + MAX_ROWS + " baris per berkas");
		}
		// Kolom wajib tersedia di berkas.
		for (String header : type.headers.subList(0, requiredColumns(type))) {
			if (!rows.get(0).containsKey(header)) {
				throw new ApiException("400", "Kolom \"" + header + "\" tidak ada di berkas; gunakan templat");
			}
		}

		BranchScope scope = branchScopeService.current();

		// Tahap 1 (pemeriksaan): tiap baris dijalankan di transaksi sendiri yang selalu dibatalkan, jadi galat satu baris tidak
		// mengganggu baris lain dan tidak ada data yang tersimpan. (JPA tidak mendukung savepoint untuk transaksi bersarang.)
		TransactionTemplate each = new TransactionTemplate(transactionManager);
		each.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		List<ImportResultDTO.RowError> errors = new ArrayList<>();
		List<String> preview = new ArrayList<>();
		int created = 0;
		int updated = 0;
		for (Map<String, String> row : rows) {
			int number = Integer.parseInt(row.get("#"));
			try {
				Outcome outcome = each.execute(status -> {
					Outcome done = apply(type, row, scope);
					status.setRollbackOnly();
					return done;
				});
				if (outcome.created()) {
					created++;
				} else {
					updated++;
				}
				if (preview.size() < PREVIEW_ROWS) {
					preview.add("Baris " + number + ": " + outcome.summary());
				}
			} catch (ApiException e) {
				errors.add(new ImportResultDTO.RowError(number, e.getMessage()));
			} catch (RuntimeException e) {
				Log.error("Impor baris " + number + " gagal", e);
				errors.add(new ImportResultDTO.RowError(number, "Data tidak dapat diproses"));
			}
		}

		// Tahap 2 (impor): hanya bila diminta dan tahap 1 bersih. Semua baris dalam satu transaksi; galat apa pun membatalkan semuanya.
		boolean applied = false;
		if (commit && errors.isEmpty()) {
			int[] current = { 0 };
			try {
				TransactionTemplate all = new TransactionTemplate(transactionManager);
				all.executeWithoutResult(status -> {
					for (Map<String, String> row : rows) {
						current[0] = Integer.parseInt(row.get("#"));
						apply(type, row, scope);
					}
				});
				applied = true;
			} catch (ApiException e) {
				errors.add(new ImportResultDTO.RowError(current[0], e.getMessage()));
			}
		}
		ImportResultDTO result = new ImportResultDTO(type.name(), !commit, applied, rows.size(), created, updated, errors, preview);
		return new ResponseModel<ImportResultDTO>().success(result);
	}

	private int requiredColumns(Type type) {
		return switch (type) {
		case PRODUCT -> 4;
		case PRICE -> 1;
		case STOCK -> 3;
		};
	}

	private Outcome apply(Type type, Map<String, String> row, BranchScope scope) {
		return switch (type) {
		case PRODUCT -> product(row, scope);
		case PRICE -> price(row, scope);
		case STOCK -> stock(row, scope);
		};
	}

	// ------------------------------------------------------------------ per jenis

	private Outcome product(Map<String, String> row, BranchScope scope) {
		String serial = clean(row.get("serial"));
		ProductDTO dto = new ProductDTO();
		String name = clean(row.get("nama"));
		String unit = clean(row.get("satuan")) == null ? null : unitCode(row.get("satuan"));
		BigDecimal price = decimal(row.get("harga_jual"), "harga_jual");
		BigDecimal cost = decimal(row.get("harga_pokok"), "harga_pokok");
		String status = clean(row.get("status"));
		if (serial != null) {
			String id = productId(serial, scope);
			dto.setId(id);
			dto.setName(name);
			dto.setUnit(unit);
			dto.setPrice(price);
			dto.setCostPrice(cost);
			dto.setStatus(status == null ? null : status.toUpperCase(Locale.ROOT));
			dto.setDescription(row.get("deskripsi") == null || row.get("deskripsi").isBlank() ? null : row.get("deskripsi").trim());
			productService.update(dto, PriceHistoryService.IMPORT);
			return new Outcome(false, "ubah " + serial + (name != null ? " " + name : ""));
		}
		if (name == null || unit == null || price == null) {
			throw new ApiException("400", "Produk baru wajib mengisi nama, satuan, dan harga_jual");
		}
		dto.setName(name);
		dto.setUnit(unit);
		dto.setPrice(price);
		dto.setCostPrice(cost);
		dto.setStatus(status == null ? null : status.toUpperCase(Locale.ROOT));
		dto.setDescription(clean(row.get("deskripsi")));
		dto.setBranchId(clean(row.get("cabang")) == null ? null : branchId(row.get("cabang"), scope));
		productService.create(dto, PriceHistoryService.IMPORT);
		return new Outcome(true, "tambah " + name + " (Rp " + price.toPlainString() + ")");
	}

	private Outcome price(Map<String, String> row, BranchScope scope) {
		String serial = clean(row.get("serial"));
		if (serial == null) {
			throw new ApiException("400", "serial wajib diisi");
		}
		BigDecimal price = decimal(row.get("harga_jual"), "harga_jual");
		BigDecimal cost = decimal(row.get("harga_pokok"), "harga_pokok");
		if (price == null && cost == null) {
			throw new ApiException("400", "Isi harga_jual dan/atau harga_pokok");
		}
		ProductDTO dto = new ProductDTO();
		dto.setId(productId(serial, scope));
		dto.setPrice(price);
		dto.setCostPrice(cost);
		productService.update(dto, PriceHistoryService.IMPORT);
		return new Outcome(false, serial + (price != null ? " harga " + price.toPlainString() : "") + (cost != null ? " pokok " + cost.toPlainString() : ""));
	}

	private Outcome stock(Map<String, String> row, BranchScope scope) {
		String branchId = branchId(row.get("cabang"), scope);
		String serial = clean(row.get("serial"));
		if (serial == null) {
			throw new ApiException("400", "serial produk wajib diisi");
		}
		BigDecimal quantity = decimal(row.get("jumlah"), "jumlah");
		if (quantity == null || quantity.signum() <= 0) {
			throw new ApiException("400", "jumlah harus lebih dari 0");
		}
		List<Object[]> found = sql.rows("SELECT p.id FROM public.product p WHERE p.serial = :serial", "serial", serial);
		if (found.isEmpty()) {
			throw new ApiException("404", "Produk " + serial + " tidak ditemukan");
		}
		ProductEntity product = stockService.visibleProduct(branchId, str(found.get(0)[0]));
		stockService.assertActive(product);
		String unit = clean(row.get("satuan")) == null ? null : unitCode(row.get("satuan"));
		BigDecimal base = stockService.toBase(product, quantity, unit, false);
		BigDecimal cost = decimal(row.get("harga_beli"), "harga_beli");
		BigDecimal unitCost = cost == null ? product.getCostPrice() : cost.multiply(quantity).divide(base, 2, RoundingMode.HALF_UP);
		String note = clean(row.get("catatan")) == null ? "Impor stok awal" : stockService.note(row.get("catatan"));
		java.time.LocalDate expiry = null;
		if (clean(row.get("kadaluwarsa")) != null) {
			try {
				expiry = java.time.LocalDate.parse(row.get("kadaluwarsa").trim());
			} catch (java.time.format.DateTimeParseException e) {
				throw new ApiException("400", "kadaluwarsa harus berformat yyyy-mm-dd");
			}
		}
		java.time.LocalDate finalExpiry = expiry;
		BigDecimal finalCost = unitCost;
		batchService.withBatch(row.get("batch"), finalExpiry, () -> stockService.applyMovement(branchId, product, base, StockMovementEntity.RECEIVE, "IMPORT", null,
				note, sessionUtility.getUserId(), finalCost));
		return new Outcome(true, "stok " + serial + " +" + base.stripTrailingZeros().toPlainString());
	}

	// ------------------------------------------------------------------ pencarian & konversi

	private String productId(String serial, BranchScope scope) {
		List<Object[]> rows = sql.rows("SELECT p.id, p.branch_id FROM public.product p WHERE p.serial = :serial", "serial", serial);
		if (rows.isEmpty() || !scope.contains(str(rows.get(0)[1]))) {
			throw new ApiException("404", "Produk " + serial + " tidak ditemukan");
		}
		return str(rows.get(0)[0]);
	}

	private String branchId(String serialRaw, BranchScope scope) {
		String serial = clean(serialRaw);
		if (serial == null) {
			if (scope.isGlobal()) {
				throw new ApiException("400", "cabang wajib diisi (serial cabang)");
			}
			return scope.getBranchId();
		}
		List<Object[]> rows = sql.rows("SELECT b.id FROM public.branch b WHERE b.serial = :serial", "serial", serial);
		if (rows.isEmpty() || !scope.contains(str(rows.get(0)[0]))) {
			throw new ApiException("404", "Cabang " + serial + " tidak ditemukan");
		}
		return str(rows.get(0)[0]);
	}

	// Kode satuan boleh diketik sebagai kode (PCS) atau nama tampilan (Pcs).
	private String unitCode(String raw) {
		String text = raw.trim();
		for (ProductUnitDTO unit : productUnitService.units()) {
			if (unit.getValue().equalsIgnoreCase(text) || (unit.getLabel() != null && unit.getLabel().equalsIgnoreCase(text))) {
				return unit.getValue();
			}
		}
		throw new ApiException("400", "Satuan \"" + text + "\" belum terdaftar");
	}

	// Angka polos ("72000", "72000.5", "72000,5"); titik dan koma sekaligus dibaca sebagai pemisah ribuan dan desimal.
	private BigDecimal decimal(String raw, String column) {
		String text = clean(raw);
		if (text == null) {
			return null;
		}
		text = text.replace(" ", "");
		// Rupiah ditulis gaya Indonesia: "15.500" atau "1.234.567" adalah pemisah ribuan, bukan desimal.
		if (column.startsWith("harga") && text.matches("\\d{1,3}(\\.\\d{3})+")) {
			text = text.replace(".", "");
		}
		if (text.contains(".") && text.contains(",")) {
			text = text.lastIndexOf(',') > text.lastIndexOf('.') ? text.replace(".", "").replace(',', '.') : text.replace(",", "");
		} else if (text.contains(",")) {
			text = text.replace(',', '.');
		}
		if (!text.matches("\\d{1,15}(\\.\\d{1,6})?")) {
			throw new ApiException("400", "Kolom " + column + " harus berupa angka tidak negatif (\"" + raw.trim() + "\")");
		}
		return new BigDecimal(text);
	}

	private Type type(String name) {
		try {
			return Type.valueOf(name == null ? "" : name.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new ApiException("400", "Jenis impor tidak dikenal");
		}
	}

	private void assertAllowed(Type type) {
		String needed = type == Type.STOCK ? "POST_API_STOCK_IN" : "PUT_API_PRODUCT";
		if (!permissionService.has(needed)) {
			throw new ApiException("403", "Akun Anda tidak memiliki akses ke impor ini");
		}
	}

	// ------------------------------------------------------------------ baca berkas

	// Setiap baris menjadi peta kolom -> teks, ditambah "#" = nomor baris di berkas (header = baris 1). Baris kosong dilewati.
	private List<Map<String, String>> parse(MultipartFile file) {
		String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
		try (InputStream in = file.getInputStream()) {
			if (name.endsWith(".xlsx")) {
				return parseXlsx(in);
			}
			if (name.endsWith(".csv")) {
				return parseCsv(in);
			}
		} catch (IOException e) {
			throw new ApiException("400", "Berkas tidak dapat dibaca");
		}
		throw new ApiException("400", "Gunakan berkas .xlsx atau .csv");
	}

	private List<Map<String, String>> parseXlsx(InputStream in) throws IOException {
		try (Workbook workbook = new XSSFWorkbook(in)) {
			Sheet sheet = workbook.getSheetAt(0);
			Row headerRow = sheet.getRow(sheet.getFirstRowNum());
			if (headerRow == null) {
				return List.of();
			}
			List<String> headers = new ArrayList<>();
			for (int c = 0; c < headerRow.getLastCellNum(); c++) {
				headers.add(header(cellText(headerRow.getCell(c))));
			}
			List<Map<String, String>> rows = new ArrayList<>();
			for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
				Row row = sheet.getRow(r);
				if (row == null) {
					continue;
				}
				List<String> values = new ArrayList<>();
				for (int c = 0; c < headers.size(); c++) {
					values.add(cellText(row.getCell(c)));
				}
				addRow(rows, headers, values, r + 1);
			}
			return rows;
		} catch (ApiException e) {
			throw e;
		} catch (Exception e) {
			throw new ApiException("400", "Berkas Excel tidak valid");
		}
	}

	private String cellText(Cell cell) {
		if (cell == null) {
			return "";
		}
		CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
		return switch (type) {
		case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
		case STRING -> cell.getStringCellValue().trim();
		case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
		default -> "";
		};
	}

	private List<Map<String, String>> parseCsv(InputStream in) throws IOException {
		List<List<String>> table = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			StringBuilder all = new StringBuilder();
			char[] buffer = new char[8192];
			int read;
			while ((read = reader.read(buffer)) > 0) {
				all.append(buffer, 0, read);
			}
			String text = all.toString();
			if (text.startsWith("﻿")) {
				text = text.substring(1);
			}
			String firstLine = text.contains("\n") ? text.substring(0, text.indexOf('\n')) : text;
			char delimiter = firstLine.chars().filter(ch -> ch == ';').count() > firstLine.chars().filter(ch -> ch == ',').count() ? ';' : ',';
			List<String> current = new ArrayList<>();
			StringBuilder cell = new StringBuilder();
			boolean quoted = false;
			for (int i = 0; i < text.length(); i++) {
				char ch = text.charAt(i);
				if (quoted) {
					if (ch == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
						cell.append('"');
						i++;
					} else if (ch == '"') {
						quoted = false;
					} else {
						cell.append(ch);
					}
				} else if (ch == '"') {
					quoted = true;
				} else if (ch == delimiter) {
					current.add(cell.toString().trim());
					cell.setLength(0);
				} else if (ch == '\n' || ch == '\r') {
					if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
						i++;
					}
					current.add(cell.toString().trim());
					cell.setLength(0);
					table.add(current);
					current = new ArrayList<>();
				} else {
					cell.append(ch);
				}
			}
			if (cell.length() > 0 || !current.isEmpty()) {
				current.add(cell.toString().trim());
				table.add(current);
			}
		}
		if (table.isEmpty()) {
			return List.of();
		}
		List<String> headers = new ArrayList<>();
		table.get(0).forEach(h -> headers.add(header(h)));
		List<Map<String, String>> rows = new ArrayList<>();
		for (int r = 1; r < table.size(); r++) {
			addRow(rows, headers, table.get(r), r + 1);
		}
		return rows;
	}

	private void addRow(List<Map<String, String>> rows, List<String> headers, List<String> values, int number) {
		Map<String, String> row = new LinkedHashMap<>();
		boolean any = false;
		for (int c = 0; c < headers.size(); c++) {
			String value = c < values.size() ? values.get(c) : "";
			if (!headers.get(c).isEmpty()) {
				row.put(headers.get(c), value);
			}
			any |= !value.isBlank();
		}
		if (any) {
			row.put("#", String.valueOf(number));
			rows.add(row);
		}
	}

	private String header(String raw) {
		return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
	}
}
