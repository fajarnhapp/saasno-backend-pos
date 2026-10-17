package com.saasno.utility.dto.article;

import java.time.LocalDateTime;

// Artikel. Permintaan memakai branchId, title, summary, content, status, pinned, expiresAt (dan id saat mengubah);
// sisanya hanya keluaran. content kosong pada daftar ringkas (dashboard). editable: user boleh mengubah artikel
// ini (izin ubah dan artikel milik cabang dalam cakupannya, bukan kiriman induk). expired: sudah lewat masa tayang.
public record ArticleDTO(
		String id,
		String branchId,
		String branchName,
		String title,
		String summary,
		String content,
		String status,
		Boolean pinned,
		LocalDateTime publishedAt,
		LocalDateTime expiresAt,
		String createdByName,
		String updatedByName,
		LocalDateTime registered,
		LocalDateTime modificated,
		Boolean editable,
		Boolean expired) {
}
