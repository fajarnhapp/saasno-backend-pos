package com.saasno.service;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.entity.ArticleEntity;
import com.saasno.repository.ArticleRepository;
import com.saasno.repository.ArticleRepository.Criteria;
import com.saasno.repository.UserRepository;
import com.saasno.utility.BranchScope;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.dto.article.ArticleDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;

// Artikel info/berita. Arah tampil: artikel milik cabang X terbaca oleh X dan seluruh turunannya (pengumuman dari
// atas ke bawah), tidak oleh induk atau saudaranya. Pembaca hanya melihat yang terbit dan belum lewat masa tayang;
// penulis (izin ubah/buat) juga melihat draf dan arsip milik cabang dalam cakupannya. Mengubah hanya untuk artikel
// milik cabang dalam cakupan user; kiriman dari induk bersifat baca saja.
@Service
public class ArticleService {

	private static final int MAX_TITLE = 200;
	private static final int MAX_SUMMARY = 500;
	private static final int MAX_CONTENT = 20000;
	private static final int MAX_PINNED = 2;
	private static final int MAX_LIMIT = 100;

	@Autowired
	private ArticleRepository articleRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private BranchScopeService branchScopeService;

	@Autowired
	private PermissionService permissionService;

	@Autowired
	private NotificationPublisher notificationPublisher;

	@Autowired
	private NotificationRecipients notificationRecipients;

	@Autowired
	private SessionUtility sessionUtility;

	// ---------------------------------------------------------------- baca

	@Transactional(readOnly = true)
	public ResponseModel<Map<String, Boolean>> access() {
		Set<String> apis = permissionService.apis();
		return new ResponseModel<Map<String, Boolean>>().success(Map.of("canWrite", apis.contains("POST_API_ARTICLE"),
				"canEdit", apis.contains("PUT_API_ARTICLE"), "canDelete", apis.contains("DELETE_API_ARTICLE")));
	}

	// Parameter: id, q (judul/ringkasan), status (khusus penulis), limit, offset.
	@Transactional(readOnly = true)
	public ResponseEntity<DataResult<ArticleDTO>> read(HttpServletRequest request) {
		BranchScope scope = branchScopeService.current();
		Set<String> apis = permissionService.apis();
		boolean writer = apis.contains("POST_API_ARTICLE") || apis.contains("PUT_API_ARTICLE");
		String status = clean(request.getParameter("status"));
		if (status != null && !writer) {
			status = ArticleEntity.PUBLISHED;
		}
		if (status != null && !Set.of(ArticleEntity.DRAFT, ArticleEntity.PUBLISHED, ArticleEntity.ARCHIVED).contains(status)) {
			throw new ApiException("400", "Status harus DRAFT, PUBLISHED, atau ARCHIVED");
		}
		Criteria criteria = new Criteria(readable(scope), scope.getIds(), writer, clean(request.getParameter("id")), status,
				clean(request.getParameter("q")), LocalDateTime.now());
		int limit = Math.min(Math.max(number(request.getParameter("limit"), 10), 1), MAX_LIMIT);
		int offset = Math.max(number(request.getParameter("offset"), 0), 0);
		List<ArticleEntity> rows = articleRepository.search(criteria, offset, limit);
		BigInteger total = BigInteger.valueOf(articleRepository.count(criteria));
		return new ResponseEntity<>(new DataResult<>(dtos(rows, scope, apis.contains("PUT_API_ARTICLE"), true), total, total),
				HttpStatus.OK);
	}

	// Artikel terbit terbaru untuk dashboard, tanpa isi; yang disematkan di atas. Hanya yang boleh dibaca semua user.
	@Transactional(readOnly = true)
	public List<ArticleDTO> latest(BranchScope scope, int limit) {
		Criteria criteria = new Criteria(readable(scope), null, false, null, null, null, LocalDateTime.now());
		return dtos(articleRepository.search(criteria, 0, limit), scope, false, false);
	}

	// Cabang yang artikelnya boleh dibaca user: cabangnya sendiri dan induk-induknya (bukan turunannya); null = semua.
	private Set<String> readable(BranchScope scope) {
		if (scope.isGlobal()) {
			return null;
		}
		Set<String> ids = new HashSet<>(scope.getVisibleIds());
		ids.removeAll(scope.getIds());
		ids.add(scope.getBranchId());
		return ids;
	}

	// ---------------------------------------------------------------- tulis

	@Transactional
	public ResponseModel<ArticleDTO> create(ArticleDTO request) {
		BranchScope scope = branchScopeService.current();
		String branchId = branchScopeService.resolveOwner(scope, request.branchId());
		ArticleEntity article = new ArticleEntity();
		article.setBranchId(branchId);
		article.setCreatedBy(sessionUtility.getUserId());
		String status = request.status() == null ? ArticleEntity.DRAFT : request.status().trim();
		if (!ArticleEntity.DRAFT.equals(status) && !ArticleEntity.PUBLISHED.equals(status)) {
			throw new ApiException("400", "Artikel baru harus berstatus DRAFT atau PUBLISHED");
		}
		boolean published = apply(article, request, status, true);
		articleRepository.store(article);
		if (published) {
			notifyPublished(article);
		}
		return new ResponseModel<ArticleDTO>().success(dtos(List.of(article), scope, true, true).get(0));
	}

	@Transactional
	public ResponseModel<ArticleDTO> update(ArticleDTO request) {
		BranchScope scope = branchScopeService.current();
		ArticleEntity article = lockOwned(scope, request.id());
		String status = request.status() == null ? article.getStatus() : request.status().trim();
		boolean published = apply(article, request, status, false);
		articleRepository.update(article);
		if (published) {
			notifyPublished(article);
		}
		return new ResponseModel<ArticleDTO>().success(dtos(List.of(article), scope, true, true).get(0));
	}

	// Artikel yang sedang terbit harus diarsipkan dulu, supaya tidak hilang dari dashboard tanpa jejak.
	@Transactional
	public ResponseModel<Void> delete(String id) {
		BranchScope scope = branchScopeService.current();
		ArticleEntity article = lockOwned(scope, id);
		if (ArticleEntity.PUBLISHED.equals(article.getStatus())) {
			throw new ApiException("409", "Artikel yang terbit harus diarsipkan dulu sebelum dihapus");
		}
		articleRepository.delete(article);
		return new ResponseModel<Void>().success(null);
	}

	// ---------------------------------------------------------------- bantu

	// Artikel milik cabang dalam cakupan user (dikunci). Di luar cakupan tapi terbaca (kiriman induk) = 403; selain itu 404.
	private ArticleEntity lockOwned(BranchScope scope, String id) {
		String key = clean(id);
		ArticleEntity article = key == null ? null : articleRepository.findLock(key);
		if (article == null) {
			throw new ApiException("404", "Artikel tidak ditemukan");
		}
		if (!scope.contains(article.getBranchId())) {
			Set<String> readable = readable(scope);
			boolean seen = readable == null || readable.contains(article.getBranchId());
			if (seen && ArticleEntity.PUBLISHED.equals(article.getStatus())) {
				throw new ApiException("403", "Artikel ini milik cabang induk dan hanya dapat dibaca");
			}
			throw new ApiException("404", "Artikel tidak ditemukan");
		}
		return article;
	}

	// Artikel yang baru terbit (bukan setiap disimpan) diberi tahu ke semua user cabang pemilik dan turunannya, selain penulisnya.
	private void notifyPublished(ArticleEntity article) {
		java.util.Set<String> readers = notificationRecipients.inSubtree(article.getBranchId());
		readers.remove(sessionUtility.getUserId());
		String preview = article.getSummary() != null ? article.getSummary()
				: article.getContent().length() > 160 ? article.getContent().substring(0, 159) + "…" : article.getContent();
		notificationPublisher.notify(readers, "ARTICLE_PUBLISHED", "Artikel baru: " + article.getTitle(), preview,
				"/article?id=" + article.getId());
	}

	// Mengisi dan memvalidasi isi artikel serta transisi status; true bila artikel ini baru saja diterbitkan.
	private boolean apply(ArticleEntity article, ArticleDTO request, String status, boolean creating) {
		if (!Set.of(ArticleEntity.DRAFT, ArticleEntity.PUBLISHED, ArticleEntity.ARCHIVED).contains(status)) {
			throw new ApiException("400", "Status harus DRAFT, PUBLISHED, atau ARCHIVED");
		}
		String title = clean(request.title());
		if (title == null || title.length() > MAX_TITLE) {
			throw new ApiException("400", "Judul wajib diisi dan tidak boleh lebih dari " + MAX_TITLE + " karakter");
		}
		String summary = clean(request.summary());
		if (summary != null && summary.length() > MAX_SUMMARY) {
			throw new ApiException("400", "Ringkasan tidak boleh lebih dari " + MAX_SUMMARY + " karakter");
		}
		String content = request.content() == null ? null : request.content().strip();
		if (content == null || content.isEmpty() || content.length() > MAX_CONTENT) {
			throw new ApiException("400", "Isi wajib diisi dan tidak boleh lebih dari " + MAX_CONTENT + " karakter");
		}
		boolean pinned = Boolean.TRUE.equals(request.pinned());
		if (pinned && !ArticleEntity.PUBLISHED.equals(status)) {
			throw new ApiException("400", "Hanya artikel yang terbit yang dapat disematkan");
		}
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime expiresAt = request.expiresAt();
		boolean becomingPublished = ArticleEntity.PUBLISHED.equals(status) && !ArticleEntity.PUBLISHED.equals(article.getStatus());
		boolean expiryChanged = creating ? expiresAt != null : !java.util.Objects.equals(expiresAt, article.getExpiresAt());
		if (ArticleEntity.PUBLISHED.equals(status) && expiresAt != null && (becomingPublished || expiryChanged)
				&& !expiresAt.isAfter(now)) {
			throw new ApiException("400", "Masa tayang harus di masa depan");
		}
		if (pinned && articleRepository.countPinned(article.getBranchId(), creating ? null : article.getId(), now) >= MAX_PINNED) {
			throw new ApiException("409", "Maksimal " + MAX_PINNED + " artikel dapat disematkan per cabang");
		}

		article.setTitle(title);
		article.setSummary(summary);
		article.setContent(content);
		article.setPinned(pinned);
		article.setExpiresAt(expiresAt);
		if (becomingPublished) {
			article.setPublishedAt(now);
		}
		article.setStatus(status);
		article.setUpdatedBy(sessionUtility.getUserId());
		return becomingPublished;
	}

	private List<ArticleDTO> dtos(List<ArticleEntity> rows, BranchScope scope, boolean canEdit, boolean withContent) {
		Map<String, String> branches = branchScopeService.names(rows.stream().map(ArticleEntity::getBranchId).distinct().toList());
		Set<String> userIds = new HashSet<>();
		rows.forEach(a -> {
			userIds.add(a.getCreatedBy());
			userIds.add(a.getUpdatedBy());
		});
		Map<String, String> users = userIds.isEmpty() ? Map.of() : userRepository.findUsernames(userIds);
		LocalDateTime now = LocalDateTime.now();
		return rows.stream().map(a -> new ArticleDTO(a.getId(), a.getBranchId(), branches.get(a.getBranchId()), a.getTitle(),
				a.getSummary(), withContent ? a.getContent() : null, a.getStatus(), a.isPinned(), a.getPublishedAt(),
				a.getExpiresAt(), users.get(a.getCreatedBy()), users.get(a.getUpdatedBy()), a.getRegistered(),
				a.getModificated(), canEdit && scope.contains(a.getBranchId()),
				a.getExpiresAt() != null && !a.getExpiresAt().isAfter(now))).toList();
	}

	private String clean(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.strip();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private int number(String value, int fallback) {
		try {
			return value == null ? fallback : Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

}
