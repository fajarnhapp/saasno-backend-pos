package com.saasno.utility.dto.backup;

import java.time.LocalDateTime;
import java.util.List;

// Satu percobaan cadangan. fileName kosong bila gagal sebelum berkas jadi; available = berkasnya masih ada di disk.
public record BackupDTO(String id, String fileName, Long sizeBytes, String status, String message, String triggeredBy,
		LocalDateTime startedAt, LocalDateTime finishedAt, boolean available) {

	// Pengaturan cadangan yang berlaku beserta riwayat terbaru.
	public record Overview(boolean enabled, int hour, int keepCount, String directory, String pgDumpPath, List<BackupDTO> backups) {
	}
}
