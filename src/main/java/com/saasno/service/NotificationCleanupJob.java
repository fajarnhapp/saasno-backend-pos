package com.saasno.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.saasno.repository.ConfigurationRepository;
import com.saasno.utility.constant.ConfigurationKey;
import com.saasno.utility.logger.Log;

import jakarta.persistence.EntityManager;

// Membersihkan notifikasi yang sudah dibaca dan lebih tua dari NOTIFICATION_RETENTION_DAYS (bawaan dan maksimal 30 hari), notifikasi
// apa pun yang lebih tua dari sebulan, lalu isi pesan yang tidak lagi dirujuk siapa pun. Aman dijalankan bersamaan
// oleh beberapa instance karena hanya menghapus.
@Component
public class NotificationCleanupJob {

	private static final int DEFAULT_DAYS = 30;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private ConfigurationRepository configurationRepository;

	@Scheduled(cron = "${saasno.notification-cleanup.cron:0 30 3 * * *}")
	public void scheduled() {
		try {
			// Dibungkus eksplisit: @Transactional pada method yang dipanggil dari dalam kelas yang sama tidak berlaku.
			transactionTemplate.executeWithoutResult(status -> cleanup());
		} catch (Exception e) {
			Log.error(e.getMessage(), e);
		}
	}

	private int cleanup() {
		LocalDateTime now = LocalDateTime.now();
		// Yang sudah dibaca dihapus setelah NOTIFICATION_RETENTION_DAYS (maksimal sebulan). Yang belum dibaca juga tidak disimpan lebih dari
		// sebulan: daftar notifikasi hanya menampilkan sebulan terakhir, jadi yang lebih tua tidak akan pernah terlihat lagi.
		int notifications = entityManager.createNativeQuery(
				"DELETE FROM public.notification WHERE (status = 1 AND registered < :readCutoff) OR registered < :maxCutoff")
				.setParameter("readCutoff", now.minusDays(days())).setParameter("maxCutoff", now.minusDays(NotificationService.MAX_AGE_DAYS))
				.executeUpdate();
		entityManager.createNativeQuery("DELETE FROM public.notification_message m WHERE m.registered < :cutoff "
				+ "AND NOT EXISTS (SELECT 1 FROM public.notification n WHERE n.notification_message_id = m.id)")
				.setParameter("cutoff", now.minusDays(days())).executeUpdate();
		return notifications;
	}

	private int days() {
		String value = configurationRepository.get(ConfigurationKey.NOTIFICATION_RETENTION_DAYS);
		try {
			return Math.min(NotificationService.MAX_AGE_DAYS, Math.max(1, value == null ? DEFAULT_DAYS : Integer.parseInt(value.trim())));
		} catch (NumberFormatException e) {
			return DEFAULT_DAYS;
		}
	}

}
