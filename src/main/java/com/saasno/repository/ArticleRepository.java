package com.saasno.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.saasno.entity.ArticleEntity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

@Repository
public class ArticleRepository extends JpaRepository<ArticleEntity, String> {

	public ArticleRepository() {
		super(ArticleEntity.class);
	}

	@Autowired
	private EntityManager entityManager;

	@Override
	public EntityManager getEntityManager() {
		return entityManager;
	}

	// Kriteria pencarian. Satu artikel terlihat bila terbit dan masih tayang di cabang yang boleh dibaca
	// (visibleIds, null = semua), atau bila user penulis dan artikel itu milik cabang dalam cakupannya
	// (ownIds, null = semua; hanya dipakai bila writer).
	public record Criteria(Set<String> visibleIds, Set<String> ownIds, boolean writer, String id, String status, String query,
			LocalDateTime now) {
	}

	public List<ArticleEntity> search(Criteria c, int offset, int limit) {
		TypedQuery<ArticleEntity> query = entityManager.createQuery("SELECT a FROM ArticleEntity a WHERE " + where(c)
				+ " ORDER BY a.pinned DESC, COALESCE(a.publishedAt, a.registered) DESC, a.id", ArticleEntity.class);
		bind(query, c);
		return query.setFirstResult(offset).setMaxResults(limit).getResultList();
	}

	public long count(Criteria c) {
		TypedQuery<Long> query = entityManager.createQuery("SELECT COUNT(a) FROM ArticleEntity a WHERE " + where(c), Long.class);
		bind(query, c);
		return query.getSingleResult();
	}

	// Jumlah artikel terbit yang disematkan dan masih tayang di satu cabang, selain artikel tertentu.
	public long countPinned(String branchId, String exceptId, LocalDateTime now) {
		return entityManager.createQuery("SELECT COUNT(a) FROM ArticleEntity a WHERE a.branchId = :branch AND a.pinned = true "
				+ "AND a.status = 'PUBLISHED' AND (a.expiresAt IS NULL OR a.expiresAt > :now) AND a.id <> :id", Long.class)
				.setParameter("branch", branchId).setParameter("now", now).setParameter("id", exceptId == null ? "" : exceptId)
				.getSingleResult();
	}

	private String where(Criteria c) {
		String live = "a.status = 'PUBLISHED' AND (a.expiresAt IS NULL OR a.expiresAt > :now)"
				+ (c.visibleIds() == null ? "" : " AND a.branchId IN :visible");
		String own = !c.writer() ? "" : c.ownIds() == null ? " OR 1 = 1" : " OR a.branchId IN :own";
		StringBuilder sql = new StringBuilder("((" + live + ")" + own + ")");
		if (c.id() != null) {
			sql.append(" AND a.id = :id");
		}
		if (c.status() != null) {
			sql.append(" AND a.status = :status");
		}
		if (c.query() != null) {
			sql.append(" AND (LOWER(a.title) LIKE :q ESCAPE '\\' OR LOWER(COALESCE(a.summary, '')) LIKE :q ESCAPE '\\')");
		}
		return sql.toString();
	}

	private void bind(TypedQuery<?> query, Criteria c) {
		query.setParameter("now", c.now());
		if (c.visibleIds() != null) {
			query.setParameter("visible", c.visibleIds());
		}
		if (c.writer() && c.ownIds() != null) {
			query.setParameter("own", c.ownIds());
		}
		if (c.id() != null) {
			query.setParameter("id", c.id());
		}
		if (c.status() != null) {
			query.setParameter("status", c.status());
		}
		if (c.query() != null) {
			query.setParameter("q", "%" + c.query().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
		}
	}

}
