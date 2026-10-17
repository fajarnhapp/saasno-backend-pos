package com.saasno.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.utility.SessionUtility;
import com.saasno.utility.dto.notification.NotificationPreferenceDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.model.ResponseModel;

import jakarta.persistence.EntityManager;

// Preferensi notifikasi milik user yang sedang login: kategori yang dimatikan disimpan di notification_mute,
// tidak ada baris berarti diterima. Hanya mengatur miliknya sendiri.
@Service
public class NotificationPreferenceService {

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private SessionUtility sessionUtility;

	@Transactional(readOnly = true)
	public ResponseModel<List<NotificationPreferenceDTO>> read() {
		Set<String> muted = muted(sessionUtility.getUserId());
		List<NotificationPreferenceDTO> list = new ArrayList<>();
		for (NotificationCatalog.Category category : NotificationCatalog.CATEGORIES) {
			list.add(dto(category, !muted.contains(category.id())));
		}
		return new ResponseModel<List<NotificationPreferenceDTO>>().success(list);
	}

	@Transactional
	public ResponseModel<List<NotificationPreferenceDTO>> update(NotificationPreferenceDTO request) {
		String category = request == null || request.getCategory() == null ? null : request.getCategory().trim();
		if (category == null || !NotificationCatalog.isCategory(category)) {
			throw new ApiException("404", "Kategori notifikasi tidak ditemukan");
		}
		if (request.getEnabled() == null) {
			throw new ApiException("400", "Status tidak boleh kosong");
		}
		String userId = sessionUtility.getUserId();
		if (request.getEnabled()) {
			entityManager.createNativeQuery("DELETE FROM public.notification_mute WHERE user_id = :user AND category = :category")
					.setParameter("user", userId).setParameter("category", category).executeUpdate();
		} else {
			entityManager.createNativeQuery("INSERT INTO public.notification_mute (user_id, category, registered) "
					+ "VALUES (:user, :category, :now) ON CONFLICT (user_id, category) DO NOTHING")
					.setParameter("user", userId).setParameter("category", category)
					.setParameter("now", LocalDateTime.now()).executeUpdate();
		}
		return read();
	}

	// Dari antara userIds, yang mematikan kategori ini (dipakai penerbit notifikasi).
	@SuppressWarnings("unchecked")
	public Set<String> mutedFor(String category, java.util.Collection<String> userIds) {
		if (category == null || userIds == null || userIds.isEmpty()) {
			return new HashSet<>();
		}
		List<String> ids = entityManager.createNativeQuery(
				"SELECT user_id FROM public.notification_mute WHERE category = :category AND user_id IN (:users)")
				.setParameter("category", category).setParameter("users", userIds).getResultList();
		return new HashSet<>(ids);
	}

	@SuppressWarnings("unchecked")
	private Set<String> muted(String userId) {
		List<String> categories = entityManager
				.createNativeQuery("SELECT category FROM public.notification_mute WHERE user_id = :user")
				.setParameter("user", userId).getResultList();
		return new HashSet<>(categories);
	}

	private NotificationPreferenceDTO dto(NotificationCatalog.Category category, boolean enabled) {
		NotificationPreferenceDTO dto = new NotificationPreferenceDTO();
		dto.setCategory(category.id());
		dto.setLabel(category.label());
		dto.setDescription(category.description());
		dto.setEnabled(enabled);
		return dto;
	}

}
