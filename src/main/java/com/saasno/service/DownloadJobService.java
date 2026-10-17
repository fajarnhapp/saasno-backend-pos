package com.saasno.service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasno.configuration.RabbitMQConfiguration;
import com.saasno.entity.ApiEntity;
import com.saasno.entity.DownloadJobEntity;
import com.saasno.entity.LogEntity;
import com.saasno.entity.MenuEntity;
import com.saasno.entity.RoleEntity;
import com.saasno.entity.UserEntity;
import com.saasno.repository.ApiRepository;
import com.saasno.repository.DownloadJobRepository;
import com.saasno.repository.LogRepository;
import com.saasno.repository.MenuRepository;
import com.saasno.repository.RoleRepository;
import com.saasno.repository.UserRepository;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.dto.branch.BranchDTO;
import com.saasno.utility.dto.customer.CustomerDTO;
import com.saasno.utility.dto.download.DownloadJobDTO;
import com.saasno.utility.dto.download.DownloadJobRequestDTO;
import com.saasno.utility.dto.product.ProductDTO;
import com.saasno.utility.dto.promo.PromoDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.logger.Log;
import com.saasno.utility.mapper.DownloadJobMapper;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.BranchScope;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.picker.DownloadJobStatus;
import com.saasno.utility.verification.StringVerification;

@Service
public class DownloadJobService {

	private static final List<String> SUPPORTED_TYPES = List.of("USER_EXPORT", "ROLE_EXPORT", "MENU_EXPORT",
			"API_EXPORT", "LOG_EXPORT", "BRANCH_EXPORT", "PRODUCT_EXPORT", "PROMO_EXPORT", "CUSTOMER_EXPORT",
			"SALE_EXPORT", "RETURN_EXPORT", "ORDER_EXPORT", "EXPENSE_EXPORT", "PETTY_CASH_EXPORT", "STOCK_EXPORT",
			"STOCK_MOVEMENT_EXPORT", "STOCK_TRANSFER_EXPORT", "STOCK_OPNAME_EXPORT", "SALE_CASH_EXPORT",
			"FINANCE_REPORT_EXPORT", "CONFIGURATION_EXPORT");

	// Unduhan hanya boleh bagi yang boleh membuka datanya di halaman: jenis unduhan -> API baca halamannya.
	private static final Map<String, String> REQUIRED_API = Map.ofEntries(
			Map.entry("USER_EXPORT", "GET_API_USER"), Map.entry("ROLE_EXPORT", "GET_API_ROLE"), Map.entry("MENU_EXPORT", "GET_API_MENU"),
			Map.entry("API_EXPORT", "GET_API_API"), Map.entry("LOG_EXPORT", "GET_API_LOG"), Map.entry("BRANCH_EXPORT", "GET_API_BRANCH"),
			Map.entry("PRODUCT_EXPORT", "GET_API_PRODUCT"), Map.entry("PROMO_EXPORT", "GET_API_PROMO"), Map.entry("CUSTOMER_EXPORT", "GET_API_CUSTOMER"),
			Map.entry("SALE_EXPORT", "GET_API_SALE"), Map.entry("RETURN_EXPORT", "GET_API_SALE_RETURN"), Map.entry("ORDER_EXPORT", "GET_API_ORDER"),
			Map.entry("EXPENSE_EXPORT", "GET_API_EXPENSE"), Map.entry("PETTY_CASH_EXPORT", "GET_API_PETTY_CASH"), Map.entry("STOCK_EXPORT", "GET_API_STOCK"),
			Map.entry("STOCK_MOVEMENT_EXPORT", "GET_API_STOCK_MOVEMENT"), Map.entry("STOCK_TRANSFER_EXPORT", "GET_API_STOCK_TRANSFER"),
			Map.entry("STOCK_OPNAME_EXPORT", "GET_API_STOCK_OPNAME"), Map.entry("SALE_CASH_EXPORT", "GET_API_SALE_CASH"),
			Map.entry("FINANCE_REPORT_EXPORT", "GET_API_FINANCE_REPORT"), Map.entry("CONFIGURATION_EXPORT", "GET_API_CONFIGURATION"));

	private static final List<String> SUPPORTED_FORMATS = List.of("csv", "excel");

	private static final String[] USER_EXPORT_HEADERS = { "Username", "Role", "Status", "OTP", "Terdaftar",
			"Diubah" };

	private static final String[] ROLE_EXPORT_HEADERS = { "ID", "Nama", "Jumlah Menu", "Jumlah API", "Terdaftar",
			"Diubah" };

	private static final String[] MENU_EXPORT_HEADERS = { "ID", "Nama", "Ikon", "URL", "Induk", "Urutan",
			"Terdaftar", "Diubah" };

	private static final String[] API_EXPORT_HEADERS = { "ID", "Nama", "Method", "Path", "Terdaftar", "Diubah" };

	private static final String[] LOG_EXPORT_HEADERS = { "Tanggal", "User ID", "Username", "Method", "URI", "IP",
			"Status", "Waktu Eksekusi (ms)", "Trace ID", "Pesan Error", "Parameter Request", "Isi Request" };

	private static final String[] BRANCH_EXPORT_HEADERS = { "Serial", "Nama", "Induk", "Jalan", "Kelurahan/Desa",
			"Kecamatan", "Kota", "Provinsi", "Negara", "Kode Pos", "Latitude", "Longitude", "Telepon", "Email",
			"Dibuat", "Diubah" };

	private static final String[] PRODUCT_EXPORT_HEADERS = { "Serial", "Nama", "Harga", "Satuan", "Status",
			"Konversi Satuan", "Harga Bertingkat", "Harga Khusus", "Dibuat", "Diubah" };

	private static final String[] PROMO_EXPORT_HEADERS = { "Serial", "Nama", "Status", "Cakupan", "Produk",
			"Jenis Syarat", "Nilai Syarat", "Jenis Hadiah", "Nilai Potongan", "Produk Hadiah", "Jumlah Hadiah",
			"Berlaku Untuk", "Berlaku Mulai", "Berlaku Sampai", "Dibuat", "Diubah" };

	private static final String[] CUSTOMER_EXPORT_HEADERS = { "Serial", "Nama", "Tipe", "PIC", "Status",
			"Jenis Kelamin", "Agama", "Tempat Lahir", "Tanggal Lahir", "Pendidikan", "Alamat", "Kontak", "Dibuat",
			"Diubah" };

	private static final String[] EDUCATION_LABELS = { "TK/Paud", "SD", "SMP", "SMA/SMK", "D1", "D2", "D3", "S1/D4",
			"S2", "S3" };

	// Excel rejects cell text longer than 32,767 characters; long request bodies are cut to fit
	private static final int EXCEL_MAX_CELL_LENGTH = 32767;

	private static final int MAX_JOBS_PER_USER = 20;

	@Autowired
	private DownloadJobRepository downloadJobRepository;

	@Autowired
	private DownloadJobMapper downloadJobMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private MenuRepository menuRepository;

	@Autowired
	private ApiRepository apiRepository;

	@Autowired
	private LogRepository logRepository;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ProductService productService;

	@Autowired
	private CustomerService customerService;

	@Autowired
	private PromoService promoService;

	@Autowired
	private ProductUnitService productUnitService;

	@Autowired
	private SessionUtility sessionUtility;

	@Autowired
	private PermissionService permissionService;

	@Autowired
	private ExportTableService exportTableService;

	@Autowired
	private BranchScopeService branchScopeService;

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private NotificationPublisher notificationPublisher;

	@Autowired
	private ObjectMapper objectMapper;

	@Value("${app.upload.dir}")
	private String uploadDir;

	@Transactional
	public ResponseModel<DownloadJobDTO> create(DownloadJobRequestDTO request) {
		if (!SUPPORTED_TYPES.contains(request.getType())) {
			throw new ApiException("400", "Jenis unduhan tidak dikenal");
		}
		if (!permissionService.has(REQUIRED_API.get(request.getType()))) {
			throw new ApiException("403", "Anda tidak memiliki akses ke data ini");
		}
		if (!SUPPORTED_FORMATS.contains(request.getFormat() == null ? "" : request.getFormat().toLowerCase())) {
			throw new ApiException("400", "Format unduhan tidak dikenal");
		}

		DownloadJobEntity job = new DownloadJobEntity();
		job.setType(request.getType());
		job.setFormat(request.getFormat().toLowerCase());
		job.setRequestedBy(sessionUtility.getUserId());
		job.setFilterJson(toFilterJson(request.getFilter()));
		downloadJobRepository.store(job);

		return new ResponseModel<DownloadJobDTO>().success(downloadJobMapper.dto(job));
	}

	// Sengaja TIDAK di dalam transaksi create() di atas: kalau di-publish sebelum transaksi
	// commit, consumer lokal (sama-sama di mesin ini) bisa saja sudah menerima & memproses
	// pesannya sebelum baris download_job benar-benar ter-commit ke Postgres — DownloadConsumer
	// akan find(jobId) dan dapat null, lalu diam-diam tidak melakukan apa-apa, sehingga status
	// job selamanya nyangkut di QUEUED. Controller memanggil ini SETELAH create() selesai
	// (dan proxy @Transactional-nya sudah commit), supaya urutannya selalu: commit dulu, baru publish.
	public void publish(String jobId) {
		rabbitTemplate.convertAndSend(
				RabbitMQConfiguration.DOWNLOAD_EXCHANGE_NAME,
				RabbitMQConfiguration.DOWNLOAD_ROUTING_KEY,
				jobId);
	}

	@Transactional(readOnly = true)
	public ResponseModel<List<DownloadJobDTO>> readMine() {
		List<DownloadJobEntity> jobs = downloadJobRepository.findByRequestedBy(sessionUtility.getUserId(),
				MAX_JOBS_PER_USER);
		return new ResponseModel<List<DownloadJobDTO>>().success(downloadJobMapper.dto(jobs));
	}

	@Transactional
	public ResponseModel<Void> delete(String id) {
		DownloadJobEntity job = findOwned(id);
		deleteFileIfExists(job);
		downloadJobRepository.delete(job);
		return new ResponseModel<Void>().success();
	}

	@Transactional(readOnly = true)
	public ResponseEntity<Resource> file(String id) {
		DownloadJobEntity job = findOwned(id);
		if (job.getStatus() != DownloadJobStatus.DONE || StringVerification.nullOrEmpty(job.getFileName())) {
			throw new ApiException("400", "Berkas belum siap");
		}

		Path filePath = Paths.get(uploadDir, "downloads", job.getFileName());
		if (!Files.exists(filePath)) {
			throw new ApiException("404", "Berkas tidak ditemukan");
		}

		String extension = "excel".equals(job.getFormat()) ? "xlsx" : "csv";
		String contentType = "excel".equals(job.getFormat())
				? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
				: "text/csv";
		String downloadName = job.getType().toLowerCase() + "-" + job.getId().substring(0, 8) + "." + extension;

		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(contentType))
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadName + "\"")
				.body(new FileSystemResource(filePath));
	}

	// ==== Dipanggil oleh DownloadConsumer — masing-masing transaksi sengaja terpisah supaya
	// status PROCESSING langsung ter-commit dan terlihat oleh polling client selagi proses
	// export (yang bisa lama) masih berjalan. ====

	@Transactional
	public void markProcessing(String jobId) {
		DownloadJobEntity job = downloadJobRepository.find(jobId);
		if (job == null) {
			return;
		}
		job.setStatus(DownloadJobStatus.PROCESSING);
		downloadJobRepository.store(job);
	}

	@Transactional
	public void exportAndComplete(String jobId) throws IOException {
		DownloadJobEntity job = downloadJobRepository.find(jobId);
		if (job == null) {
			return;
		}

		String[] headers;
		List<String[]> rows;
		DataFilter dataFilter = buildFullFilter(job.getFilterJson());
		// Job berjalan async tanpa session, jadi cakupan cabang dihitung dari user pemintanya.
		BranchScope scope = branchScopeService.forUser(job.getRequestedBy());

		switch (job.getType()) {
			case "USER_EXPORT":
				headers = USER_EXPORT_HEADERS;
				rows = userRepository.dataTable(scope.apply(dataFilter, "branchId")).getData().stream().map(this::toUserRow).toList();
				break;
			case "ROLE_EXPORT":
				headers = ROLE_EXPORT_HEADERS;
				rows = roleRepository.dataTable(dataFilter).getData().stream().map(this::toRoleRow).toList();
				break;
			case "MENU_EXPORT":
				headers = MENU_EXPORT_HEADERS;
				rows = menuRepository.dataTable(dataFilter).getData().stream().map(this::toMenuRow).toList();
				break;
			case "API_EXPORT":
				headers = API_EXPORT_HEADERS;
				rows = apiRepository.dataTable(dataFilter).getData().stream().map(this::toApiRow).toList();
				break;
			case "LOG_EXPORT":
				headers = LOG_EXPORT_HEADERS;
				rows = logRepository.dataTable(dataFilter).getData().stream().map(this::toLogRow).toList();
				break;
			case "BRANCH_EXPORT":
				headers = BRANCH_EXPORT_HEADERS;
				rows = branchService.find(dataFilter, scope).getData().stream().map(this::toBranchRow).toList();
				break;
			case "PRODUCT_EXPORT": {
				headers = PRODUCT_EXPORT_HEADERS;
				Map<String, String> unitLabels = unitLabels();
				rows = productService.find(dataFilter, scope).getData().stream().map(p -> toProductRow(p, unitLabels))
						.toList();
				break;
			}
			case "PROMO_EXPORT":
				headers = PROMO_EXPORT_HEADERS;
				rows = promoService.find(dataFilter, scope).getData().stream().map(this::toPromoRow).toList();
				break;
			case "CUSTOMER_EXPORT":
				headers = CUSTOMER_EXPORT_HEADERS;
				rows = customerService.find(dataFilter, scope).getData().stream().map(this::toCustomerRow).toList();
				break;
			default: {
				ExportTableService.Table table = exportTableService.build(job.getType(), parseFilterJson(job.getFilterJson()), scope);
				headers = table.headers();
				rows = table.rows();
			}
		}

		Path directory = Paths.get(uploadDir, "downloads");
		Files.createDirectories(directory);

		String extension = "excel".equals(job.getFormat()) ? "xlsx" : "csv";
		String fileName = job.getId() + "." + extension;
		Path filePath = directory.resolve(fileName);

		if ("excel".equals(job.getFormat())) {
			writeExcel(headers, rows, filePath);
		} else {
			writeCsv(headers, rows, filePath);
		}

		job.setFileName(fileName);
		job.setStatus(DownloadJobStatus.DONE);
		downloadJobRepository.store(job);
		notificationPublisher.notify(List.of(job.getRequestedBy()), "DOWNLOAD_DONE", "Unduhan selesai",
				"Unduhan " + exportLabel(job.getType()) + " (" + ("excel".equals(job.getFormat()) ? "Excel" : "CSV")
						+ ") sudah siap diunduh.",
				null);
	}

	// Nama data yang diunduh untuk pesan notifikasi, mis. PRODUCT_EXPORT -> "produk".
	private String exportLabel(String type) {
		if (type == null) {
			return "data";
		}
		return switch (type) {
			case "USER_EXPORT" -> "user";
			case "ROLE_EXPORT" -> "role";
			case "MENU_EXPORT" -> "menu";
			case "API_EXPORT" -> "API";
			case "LOG_EXPORT" -> "audit log";
			case "BRANCH_EXPORT" -> "cabang";
			case "PRODUCT_EXPORT" -> "produk";
			case "PROMO_EXPORT" -> "promo";
			case "CUSTOMER_EXPORT" -> "customer";
			case "SALE_EXPORT" -> "riwayat penjualan";
			case "RETURN_EXPORT" -> "retur";
			case "ORDER_EXPORT" -> "pesanan online";
			case "EXPENSE_EXPORT" -> "biaya";
			case "PETTY_CASH_EXPORT" -> "kas kecil";
			case "STOCK_EXPORT" -> "saldo stok";
			case "STOCK_MOVEMENT_EXPORT" -> "mutasi stok";
			case "STOCK_TRANSFER_EXPORT" -> "transfer stok";
			case "STOCK_OPNAME_EXPORT" -> "stok opname";
			case "SALE_CASH_EXPORT" -> "kas harian";
			case "FINANCE_REPORT_EXPORT" -> "laporan keuangan";
			case "CONFIGURATION_EXPORT" -> "konfigurasi";
			default -> "data";
		};
	}

	private DataFilter buildFullFilter(String filterJson) {
		Map<String, String> filterParams = parseFilterJson(filterJson);
		filterParams.remove(DataFilter.LIMIT);
		filterParams.remove(DataFilter.OFFSET);
		DataFilter dataFilter = new DataFilter(filterParams);
		dataFilter.setLimit(String.valueOf(Integer.MAX_VALUE));
		dataFilter.setOffset("0");
		return dataFilter;
	}

	@Transactional
	public void markFailed(String jobId, String message) {
		DownloadJobEntity job = downloadJobRepository.find(jobId);
		if (job == null) {
			return;
		}
		job.setStatus(DownloadJobStatus.FAILED);
		job.setErrorMessage(StringVerification.nullOrEmpty(message) ? "Gagal memproses unduhan" : message);
		downloadJobRepository.store(job);
		notificationPublisher.notify(List.of(job.getRequestedBy()), "DOWNLOAD_FAILED", "Unduhan gagal",
				"Unduhan " + exportLabel(job.getType()) + " gagal diproses. Silakan coba lagi.", null);
	}

	private String[] toUserRow(UserEntity user) {
		String roles = user.getRoles() == null ? "" : user.getRoles().stream()
				.map(RoleEntity::getName)
				.collect(Collectors.joining(", "));
		return new String[] {
				user.getUsername(),
				roles,
				user.getStatus() != null ? user.getStatus().name() : "",
				Boolean.TRUE.equals(user.getTwoFactorAuth()) ? "Aktif" : "Nonaktif",
				user.getRegistered() != null ? user.getRegistered().toString() : "",
				user.getModificated() != null ? user.getModificated().toString() : "",
		};
	}

	private String[] toRoleRow(RoleEntity role) {
		return new String[] {
				role.getId(),
				role.getName(),
				String.valueOf(role.getMenus() == null ? 0 : role.getMenus().size()),
				String.valueOf(role.getApis() == null ? 0 : role.getApis().size()),
				role.getRegistered() != null ? role.getRegistered().toString() : "",
				role.getModificated() != null ? role.getModificated().toString() : "",
		};
	}

	private String[] toMenuRow(MenuEntity menu) {
		return new String[] {
				menu.getId(),
				menu.getName(),
				menu.getIcon() != null ? menu.getIcon() : "",
				menu.getUrl(),
				menu.getParent() != null ? menu.getParent().getId() : "",
				menu.getOrderIndex() != null ? menu.getOrderIndex().toString() : "",
				menu.getRegistered() != null ? menu.getRegistered().toString() : "",
				menu.getModificated() != null ? menu.getModificated().toString() : "",
		};
	}

	private String[] toApiRow(ApiEntity api) {
		return new String[] {
				api.getId(),
				api.getName(),
				api.getMethod(),
				api.getPath(),
				api.getRegistered() != null ? api.getRegistered().toString() : "",
				api.getModificated() != null ? api.getModificated().toString() : "",
		};
	}

	private String[] toLogRow(LogEntity log) {
		return new String[] {
				log.getRegistered() != null ? log.getRegistered().toString() : "",
				orEmpty(log.getUserId()),
				orEmpty(log.getUsername()),
				orEmpty(log.getMethod()),
				orEmpty(log.getUri()),
				orEmpty(log.getClientIp()),
				String.valueOf(log.getStatusCode()),
				String.valueOf(log.getExecutionTimeMs()),
				orEmpty(log.getTraceId()),
				orEmpty(log.getErrorMessage()),
				orEmpty(log.getRequestParam()),
				orEmpty(log.getRequestBody()),
		};
	}

	private String[] toBranchRow(BranchDTO b) {
		return new String[] {
				orEmpty(b.getSerial()),
				orEmpty(b.getName()),
				orEmpty(b.getParentName()),
				orEmpty(b.getStreet()),
				orEmpty(b.getVillage()),
				orEmpty(b.getDistrict()),
				orEmpty(b.getCity()),
				orEmpty(b.getProvince()),
				orEmpty(b.getCountry()),
				orEmpty(b.getPostalCode()),
				orEmpty(b.getLatitude()),
				orEmpty(b.getLongitude()),
				orEmpty(b.getPhone()),
				orEmpty(b.getEmail()),
				b.getRegistered() != null ? b.getRegistered().toString() : "",
				b.getModificated() != null ? b.getModificated().toString() : "",
		};
	}

	private String[] toProductRow(ProductDTO p, Map<String, String> unitLabels) {
		String conversions = p.getConversions() == null ? "" : p.getConversions().stream()
				.map(c -> "1 " + unitLabel(unitLabels, c.getToUnit()) + " = " + number(c.getValue()) + " "
						+ unitLabel(unitLabels, c.getFromUnit()))
				.collect(Collectors.joining("; "));
		String tiers = p.getPriceTiers() == null ? "" : p.getPriceTiers().stream()
				.map(t -> ">= " + number(t.getMinQuantity()) + " " + unitLabel(unitLabels, p.getUnit()) + ": "
						+ number(t.getPrice()))
				.collect(Collectors.joining("; "));
		String customerPrices = p.getCustomerPrices() == null ? "" : p.getCustomerPrices().stream()
				.map(c -> ("TYPE".equals(c.getTarget()) ? "INDIVIDU".equals(c.getCustomerType()) ? "Individu" : "Bisnis"
						: orEmpty(c.getCustomerName())) + ": " + number(c.getPrice()))
				.collect(Collectors.joining("; "));
		return new String[] {
				orEmpty(p.getSerial()),
				orEmpty(p.getName()),
				number(p.getPrice()),
				unitLabel(unitLabels, p.getUnit()),
				"ACTIVE".equals(p.getStatus()) ? "Aktif" : "Nonaktif",
				conversions,
				tiers,
				customerPrices,
				p.getRegistered() != null ? p.getRegistered().toString() : "",
				p.getModificated() != null ? p.getModificated().toString() : "",
		};
	}

	private String[] toCustomerRow(CustomerDTO c) {
		var profile = c.getProfile();
		boolean individual = "INDIVIDU".equals(c.getType());
		String education = profile.getEducation() != null && profile.getEducation() >= 0
				&& profile.getEducation() < EDUCATION_LABELS.length ? EDUCATION_LABELS[profile.getEducation()] : "";
		String addresses = c.getAddresses() == null ? "" : c.getAddresses().stream()
				.map(a -> "[" + a.getType() + "] " + java.util.stream.Stream
						.of(a.getStreet(), a.getVillage(), a.getDistrict(), a.getCity(), a.getProvince(),
								a.getPostalCode(), a.getCountry())
						.filter(s -> s != null && !s.isEmpty()).collect(Collectors.joining(", ")))
				.collect(Collectors.joining("; "));
		String contacts = c.getContacts() == null ? "" : c.getContacts().stream()
				.map(k -> k.getType() + ": " + k.getValue()).collect(Collectors.joining("; "));
		return new String[] {
				orEmpty(c.getSerial()),
				orEmpty(profile.getName()),
				individual ? "Individu" : "Bisnis",
				orEmpty(c.getPic()),
				"ACTIVE".equals(c.getStatus()) ? "Aktif" : "Nonaktif",
				"MALE".equals(profile.getGender()) ? "Laki-laki" : "FEMALE".equals(profile.getGender()) ? "Perempuan" : "",
				orEmpty(profile.getReligion()),
				orEmpty(profile.getBirthPlace()),
				profile.getBirthDate() != null ? profile.getBirthDate().toString() : "",
				education,
				addresses,
				contacts,
				c.getRegistered() != null ? c.getRegistered().toString() : "",
				c.getModificated() != null ? c.getModificated().toString() : "",
		};
	}

	private String[] toPromoRow(PromoDTO p) {
		boolean freeProduct = "FREE_PRODUCT".equals(p.getRewardType());
		String audience = switch (orEmpty(p.getAudience())) {
			case "TYPE" -> "Tipe: " + ("INDIVIDU".equals(p.getCustomerType()) ? "Individu" : "Bisnis");
			case "CUSTOMER" -> p.getCustomers() == null ? "" : p.getCustomers().stream()
					.map(c -> orEmpty(c.getName())).collect(Collectors.joining("; "));
			default -> "Semua Customer";
		};
		return new String[] {
				orEmpty(p.getSerial()),
				orEmpty(p.getName()),
				"ACTIVE".equals(p.getStatus()) ? "Aktif" : "Nonaktif",
				"PRODUCT".equals(p.getScope()) ? "Per Produk" : "Total Semua Produk",
				orEmpty(p.getProductName()),
				"AMOUNT".equals(p.getConditionType()) ? "Minimal Nilai Belanja" : "Minimal Jumlah",
				number(p.getMinValue()),
				switch (orEmpty(p.getRewardType())) {
					case "DISCOUNT_FIXED" -> "Potongan Harga (Rp)";
					case "DISCOUNT_PERCENT" -> "Potongan Persen (%)";
					case "FREE_PRODUCT" -> "Gratis Produk";
					default -> "";
				},
				freeProduct ? "" : number(p.getDiscountValue()),
				orEmpty(p.getRewardProductName()),
				freeProduct ? number(p.getRewardQuantity()) : "",
				audience,
				p.getValidFrom() != null ? p.getValidFrom().toString() : "",
				p.getValidUntil() != null ? p.getValidUntil().toString() : "",
				p.getRegistered() != null ? p.getRegistered().toString() : "",
				p.getModificated() != null ? p.getModificated().toString() : "",
		};
	}

	// Label satuan dari configuration PRODUCT_UNIT; kalau tidak tersedia, kode satuan dipakai apa adanya.
	private Map<String, String> unitLabels() {
		try {
			return productUnitService.units().stream()
					.collect(Collectors.toMap(u -> u.getValue(), u -> u.getLabel(), (a, b) -> a));
		} catch (Exception e) {
			return new HashMap<>();
		}
	}

	private String unitLabel(Map<String, String> labels, String code) {
		return labels.getOrDefault(code, orEmpty(code));
	}

	// Angka tanpa nol di belakang koma dan tanpa notasi ilmiah, supaya terbaca sebagai angka di CSV/Excel.
	private String number(BigDecimal value) {
		if (value == null) {
			return "";
		}
		BigDecimal stripped = value.stripTrailingZeros();
		return (stripped.scale() < 0 ? stripped.setScale(0) : stripped).toPlainString();
	}

	private String orEmpty(String value) {
		return value != null ? value : "";
	}

	private void writeCsv(String[] headers, List<String[]> rows, Path filePath) throws IOException {
		try (var writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
			writer.write(String.join(",", headers));
			writer.newLine();
			for (String[] row : rows) {
				writer.write(Arrays.stream(row).map(this::csvEscape).collect(Collectors.joining(",")));
				writer.newLine();
			}
		}
	}

	private String csvEscape(String value) {
		if (value == null) {
			return "";
		}
		value = neutralizeFormula(value);
		if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
			return "\"" + value.replace("\"", "\"\"") + "\"";
		}
		return value;
	}

	// Sel CSV yang diawali = + - @ dibaca Excel sebagai rumus; nama atau catatan dari pembeli (data tidak tepercaya) bisa
	// memanfaatkannya. Angka biasa (termasuk negatif) dibiarkan, selebihnya diberi apostrof di depan.
	private String neutralizeFormula(String value) {
		if (value.isEmpty()) {
			return value;
		}
		char first = value.charAt(0);
		boolean risky = first == '=' || first == '@' || first == '\t' || first == '\r'
				|| ((first == '+' || first == '-') && !value.matches("[+-]?\\d+(\\.\\d+)?"));
		return risky ? "'" + value : value;
	}

	private void writeExcel(String[] headers, List<String[]> rows, Path filePath) throws IOException {
		try (XSSFWorkbook workbook = new XSSFWorkbook(); OutputStream os = Files.newOutputStream(filePath)) {
			Sheet sheet = workbook.createSheet("Data");

			CellStyle headerStyle = workbook.createCellStyle();
			Font headerFont = workbook.createFont();
			headerFont.setBold(true);
			headerStyle.setFont(headerFont);

			Row headerRow = sheet.createRow(0);
			for (int i = 0; i < headers.length; i++) {
				Cell cell = headerRow.createCell(i);
				cell.setCellValue(headers[i]);
				cell.setCellStyle(headerStyle);
			}

			int rowIndex = 1;
			for (String[] values : rows) {
				Row row = sheet.createRow(rowIndex++);
				for (int i = 0; i < values.length; i++) {
					String value = values[i];
					if (value != null && value.length() > EXCEL_MAX_CELL_LENGTH) {
						value = value.substring(0, EXCEL_MAX_CELL_LENGTH);
					}
					row.createCell(i).setCellValue(value);
				}
			}

			for (int i = 0; i < headers.length; i++) {
				sheet.autoSizeColumn(i);
			}

			workbook.write(os);
		}
	}

	private DownloadJobEntity findOwned(String id) {
		DownloadJobEntity job = downloadJobRepository.find(id);
		if (job == null || !job.getRequestedBy().equals(sessionUtility.getUserId())) {
			throw new ApiException("404", "Unduhan tidak ditemukan");
		}
		return job;
	}

	private void deleteFileIfExists(DownloadJobEntity job) {
		if (StringVerification.nullOrEmpty(job.getFileName())) {
			return;
		}
		try {
			Files.deleteIfExists(Paths.get(uploadDir, "downloads", job.getFileName()));
		} catch (IOException e) {
			Log.error("Failed to delete download file " + job.getId(), e);
		}
	}

	private String toFilterJson(Map<String, String> filter) {
		try {
			return objectMapper.writeValueAsString(filter != null ? filter : Map.of());
		} catch (JsonProcessingException e) {
			return "{}";
		}
	}

	private Map<String, String> parseFilterJson(String filterJson) {
		if (StringVerification.nullOrEmpty(filterJson)) {
			return new HashMap<>();
		}
		try {
			return objectMapper.readValue(filterJson, new TypeReference<Map<String, String>>() {
			});
		} catch (Exception e) {
			return new HashMap<>();
		}
	}

}
