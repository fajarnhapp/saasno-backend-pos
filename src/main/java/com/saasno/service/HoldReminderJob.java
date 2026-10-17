package com.saasno.service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.saasno.repository.ConfigurationRepository;
import com.saasno.utility.constant.ConfigurationKey;
import com.saasno.utility.logger.Log;

import jakarta.persistence.EntityManager;

// Mengingatkan kasir yang menahan keranjang lebih lama dari HOLD_STALE_HOURS (bawaan 12 jam), sekali per keranjang.
// Keranjang tetap tidak dibatalkan otomatis; hanya pengingat. SKIP LOCKED membuat beberapa instance tidak mengirim ganda.
@Component
public class HoldReminderJob {

	private static final int DEFAULT_HOURS = 12;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private NotificationPublisher notificationPublisher;

	@Autowired
	private ConfigurationRepository configurationRepository;

	@Scheduled(cron = "${saasno.hold-reminder.cron:0 10 * * * *}")
	public void scheduled() {
		try {
			// Dibungkus eksplisit: @Transactional pada method yang dipanggil dari dalam kelas yang sama tidak berlaku.
			transactionTemplate.executeWithoutResult(status -> remind());
		} catch (Exception e) {
			Log.error(e.getMessage(), e);
		}
	}

	@SuppressWarnings("unchecked")
	private int remind() {
		LocalDateTime now = LocalDateTime.now();
		List<Object[]> rows = entityManager.createNativeQuery("SELECT h.id, h.serial, h.label, h.created_by, h.registered "
				+ "FROM public.sale_hold h WHERE h.status = 'HELD' AND h.stale_notified_at IS NULL AND h.registered < :cutoff "
				+ "ORDER BY h.registered FOR UPDATE SKIP LOCKED")
				.setParameter("cutoff", now.minusHours(hours())).getResultList();
		for (Object[] row : rows) {
			LocalDateTime held = row[4] instanceof Timestamp t ? t.toLocalDateTime() : (LocalDateTime) row[4];
			long hours = Duration.between(held, now).toHours();
			String label = row[2] == null || ((String) row[2]).isBlank() ? "" : " (" + row[2] + ")";
			notificationPublisher.notify(List.of((String) row[3]), "HOLD_STALE", "Transaksi tertahan terlalu lama",
					"Keranjang " + row[1] + label + " sudah ditahan " + hours + " jam. Lanjutkan pembayaran atau buang bila batal.",
					"/transaction/cashier");
			entityManager.createNativeQuery("UPDATE public.sale_hold SET stale_notified_at = :now WHERE id = :id")
					.setParameter("now", now).setParameter("id", row[0]).executeUpdate();
		}
		return rows.size();
	}

	private int hours() {
		String value = configurationRepository.get(ConfigurationKey.HOLD_STALE_HOURS);
		try {
			return Math.max(1, value == null ? DEFAULT_HOURS : Integer.parseInt(value.trim()));
		} catch (NumberFormatException e) {
			return DEFAULT_HOURS;
		}
	}

}
