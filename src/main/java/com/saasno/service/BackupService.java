package com.saasno.service;

import static com.saasno.service.AnalyticsSupport.lng;
import static com.saasno.service.AnalyticsSupport.str;
import static com.saasno.service.AnalyticsSupport.time;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.saasno.repository.ConfigurationRepository;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.constant.ConfigurationKey;
import com.saasno.utility.dto.backup.BackupDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.generator.ID;
import com.saasno.utility.logger.Log;
import com.saasno.utility.model.ResponseModel;

// Cadangan database dengan pg_dump (format kustom, untuk pg_restore). Berjalan harian pada BACKUP_HOUR bila BACKUP_ENABLED, atau
// manual lewat tombol. Berkas disimpan di BACKUP_DIR; hanya BACKUP_KEEP_COUNT yang terbaru dipertahankan. Pemulihan sengaja tidak
// ada di aplikasi (berbahaya): jalankan pg_restore secara manual. Nama berkas selalu dibuat server, tidak pernah dari permintaan.
@Service
public class BackupService {

	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final Pattern JDBC = Pattern.compile("jdbc:postgresql://([^:/]+)(?::(\\d+))?/([^?]+).*");
	private static final int TIMEOUT_MINUTES = 30;
	private static final int HISTORY = 30;

	@Value("${spring.datasource.url}")
	private String datasourceUrl;

	@Value("${spring.datasource.username}")
	private String datasourceUser;

	@Value("${spring.datasource.password}")
	private String datasourcePassword;

	@Autowired
	private SqlHelper sql;

	@Autowired
	private ConfigurationRepository configurationRepository;

	@Autowired
	private SessionUtility sessionUtility;

	@Autowired
	private TransactionTemplate transactionTemplate;

	// ------------------------------------------------------------------ baca

	public ResponseModel<BackupDTO.Overview> overview() {
		List<BackupDTO> list = transactionTemplate.execute(status -> {
			List<BackupDTO> rows = new ArrayList<>();
			for (Object[] r : sql.rows("SELECT b.id, b.file_name, b.size_bytes, b.status, b.message, u.username, b.started_at, b.finished_at FROM public.backup_log b "
					+ "LEFT JOIN public.\"user\" u ON u.id = b.triggered_by ORDER BY b.started_at DESC LIMIT " + HISTORY)) {
				String file = str(r[1]);
				rows.add(new BackupDTO(str(r[0]), file, r[2] == null ? null : lng(r[2]), str(r[3]), str(r[4]), str(r[5]), time(r[6]), time(r[7]),
						file != null && Files.exists(directory().resolve(file))));
			}
			return rows;
		});
		return new ResponseModel<BackupDTO.Overview>().success(new BackupDTO.Overview(enabled(), hour(), keepCount(), directory().toString(), pgDump(), list));
	}

	// Berkas untuk diunduh; hanya yang tercatat sebagai OK di riwayat dan masih ada di BACKUP_DIR.
	public Resource file(String id) {
		List<Object[]> rows = id == null ? List.of()
				: transactionTemplate.execute(status -> sql.rows("SELECT b.file_name FROM public.backup_log b WHERE b.id = :id AND b.status = 'OK'", "id", id.trim()));
		if (rows == null || rows.isEmpty() || rows.get(0)[0] == null) {
			throw new ApiException("404", "Berkas cadangan tidak ditemukan");
		}
		Path path = directory().resolve(str(rows.get(0)[0])).normalize();
		if (!path.startsWith(directory()) || !Files.isRegularFile(path)) {
			throw new ApiException("404", "Berkas cadangan sudah tidak ada di server");
		}
		return new FileSystemResource(path);
	}

	// ------------------------------------------------------------------ jalankan

	public ResponseModel<BackupDTO> runNow() {
		return new ResponseModel<BackupDTO>().success(run(sessionUtility.getUserId()));
	}

	// Pemeriksaan tiap jam: menjalankan sekali per hari pada jam yang ditentukan. Kunci advisory mencegah dua instance menjalankannya bersamaan.
	@Scheduled(cron = "0 5 * * * *")
	public void scheduled() {
		try {
			if (!enabled() || LocalDateTime.now().getHour() != hour()) {
				return;
			}
			Boolean already = transactionTemplate.execute(status -> {
				sql.lock("backup_daily");
				return !sql.rows("SELECT 1 FROM public.backup_log b WHERE b.triggered_by IS NULL AND b.started_at >= :today", "today", LocalDate.now().atStartOfDay()).isEmpty();
			});
			if (!Boolean.TRUE.equals(already)) {
				run(null);
			}
		} catch (Exception e) {
			Log.error("Backup terjadwal gagal dijalankan", e);
		}
	}

	private BackupDTO run(String userId) {
		Matcher url = JDBC.matcher(datasourceUrl);
		if (!url.matches()) {
			throw new ApiException("500", "Alamat database tidak dikenali untuk cadangan");
		}
		String id = ID.uuid();
		LocalDateTime started = LocalDateTime.now();
		String fileName = "saasno-" + started.format(STAMP) + ".dump";
		transactionTemplate.executeWithoutResult(status -> sql.execute(
				"INSERT INTO public.backup_log (id, file_name, status, triggered_by, started_at) VALUES (:id, :file, 'RUNNING', :user, :now)",
				"id", id, "file", fileName, "user", userId, "now", started));

		String status = "OK";
		String message = null;
		Long size = null;
		try {
			Path dir = directory();
			Files.createDirectories(dir);
			Path target = dir.resolve(fileName);
			File error = Files.createTempFile("saasno-pgdump", ".log").toFile();
			ProcessBuilder builder = new ProcessBuilder(pgDump(), "-h", url.group(1), "-p", url.group(2) == null ? "5432" : url.group(2),
					"-U", datasourceUser, "-d", url.group(3), "-Fc")
					.redirectOutput(target.toFile()).redirectError(error);
			builder.environment().put("PGPASSWORD", datasourcePassword);
			Process process = builder.start();
			if (!process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
				process.destroyForcibly();
				throw new IOException("pg_dump melebihi " + TIMEOUT_MINUTES + " menit");
			}
			if (process.exitValue() != 0) {
				String detail = Files.readString(error.toPath()).trim();
				Files.deleteIfExists(target);
				throw new IOException("pg_dump gagal (" + process.exitValue() + "): " + (detail.isEmpty() ? "tanpa pesan" : detail));
			}
			size = Files.size(target);
			if (size == 0) {
				Files.deleteIfExists(target);
				throw new IOException("Berkas cadangan kosong");
			}
			Files.deleteIfExists(error.toPath());
			prune();
		} catch (IOException e) {
			status = "FAILED";
			message = e.getMessage() == null ? "Cadangan gagal" : e.getMessage();
			Log.error("Backup gagal: " + message, e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			status = "FAILED";
			message = "Cadangan terhenti";
		}
		String finalStatus = status;
		String finalMessage = message == null ? null : message.substring(0, Math.min(message.length(), 500));
		Long finalSize = size;
		transactionTemplate.executeWithoutResult(s -> sql.execute(
				"UPDATE public.backup_log SET status = :status, message = :message, size_bytes = :size, finished_at = :now, file_name = :file WHERE id = :id",
				"status", finalStatus, "message", finalMessage, "size", finalSize, "now", LocalDateTime.now(), "id", id,
				"file", finalStatus.equals("OK") ? fileName : null));
		if (finalStatus.equals("FAILED")) {
			throw new ApiException("500", finalMessage);
		}
		return new BackupDTO(id, fileName, finalSize, finalStatus, null, null, started, LocalDateTime.now(), true);
	}

	// Menghapus berkas cadangan terlama di luar BACKUP_KEEP_COUNT (hanya berkas bernama saasno-*.dump milik aplikasi ini).
	private void prune() throws IOException {
		File[] files = directory().toFile().listFiles((dir, name) -> name.matches("saasno-\\d{8}-\\d{6}\\.dump"));
		if (files == null || files.length <= keepCount()) {
			return;
		}
		java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName).reversed());
		for (int i = keepCount(); i < files.length; i++) {
			Files.deleteIfExists(files[i].toPath());
		}
	}

	// ------------------------------------------------------------------ pengaturan

	private boolean enabled() {
		return "true".equalsIgnoreCase(String.valueOf(configurationRepository.get(ConfigurationKey.BACKUP_ENABLED)).trim());
	}

	private int hour() {
		return number(ConfigurationKey.BACKUP_HOUR, 2, 0, 23);
	}

	private int keepCount() {
		return number(ConfigurationKey.BACKUP_KEEP_COUNT, 14, 1, 365);
	}

	private Path directory() {
		String value = configurationRepository.get(ConfigurationKey.BACKUP_DIR);
		return Paths.get(value == null || value.isBlank() ? "/var/backups/saasno" : value.trim()).toAbsolutePath().normalize();
	}

	private String pgDump() {
		String value = configurationRepository.get(ConfigurationKey.BACKUP_PG_DUMP_PATH);
		return value == null || value.isBlank() ? "pg_dump" : value.trim();
	}

	private int number(ConfigurationKey key, int fallback, int min, int max) {
		try {
			int value = Integer.parseInt(String.valueOf(configurationRepository.get(key)).trim());
			return value < min || value > max ? fallback : value;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}
