package com.saasno.utility.dto.dataimport;

import java.util.List;

// Hasil pemeriksaan/impor satu berkas. applied = data benar-benar tersimpan (hanya bila tidak ada galat dan bukan pemeriksaan).
// Galat satu baris membatalkan seluruh impor: tidak ada data yang tersimpan sebagian.
public record ImportResultDTO(String type, boolean dryRun, boolean applied, int totalRows, int created, int updated, List<RowError> errors,
		List<String> preview) {

	public record RowError(int row, String message) {
	}
}
