package com.saasno.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

// Pembantu query native untuk layanan yang menulis/membaca tabel tanpa entitas JPA (supplier, pembelian). Parameter hanya
// dipasang bila namanya muncul di SQL, dan hasil satu kolom diseragamkan menjadi Object[].
@Component
public class SqlHelper {

	@Autowired
	private EntityManager entityManager;

	public List<Object[]> rows(String sql, Object... kv) {
		return rows(sql, map(kv));
	}

	public List<Object[]> rows(String sql, Map<String, Object> params) {
		Query query = entityManager.createNativeQuery(sql);
		params.forEach((name, value) -> {
			if (sql.contains(":" + name)) {
				query.setParameter(name, value);
			}
		});
		List<?> result = query.getResultList();
		List<Object[]> rows = new ArrayList<>(result.size());
		for (Object row : result) {
			rows.add(row instanceof Object[] array ? array : new Object[] { row });
		}
		return rows;
	}

	public int execute(String sql, Object... kv) {
		return execute(sql, map(kv));
	}

	public int execute(String sql, Map<String, Object> params) {
		Query query = entityManager.createNativeQuery(sql);
		params.forEach((name, value) -> {
			if (sql.contains(":" + name)) {
				query.setParameter(name, value);
			}
		});
		return query.executeUpdate();
	}

	// Kunci advisory tingkat transaksi, untuk nomor urut dan pemeriksaan unik yang tidak punya baris untuk dikunci.
	public void lock(String key) {
		entityManager.createNativeQuery("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(:key))) AS locked")
				.setParameter("key", key).getSingleResult();
	}

	public static Map<String, Object> map(Object... kv) {
		Map<String, Object> params = new HashMap<>();
		for (int i = 0; i + 1 < kv.length; i += 2) {
			params.put((String) kv[i], kv[i + 1]);
		}
		return params;
	}

	public static String clean(String value) {
		String trimmed = value == null ? null : value.trim();
		return trimmed == null || trimmed.isEmpty() ? null : trimmed;
	}
}
