package com.saasno.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.saasno.configuration.RabbitMQConfiguration;
import com.saasno.entity.NotificationEntity;
import com.saasno.entity.NotificationMessageEntity;
import com.saasno.repository.NotificationMessageRepository;
import com.saasno.repository.NotificationRepository;
import com.saasno.utility.logger.Log;

import jakarta.persistence.EntityManager;

// Membuat notifikasi untuk banyak user sekaligus di dalam transaksi pemanggil (jadi ikut batal bila kejadiannya batal),
// lalu mengabari frontend lewat RabbitMQ SETELAH commit supaya tidak ada sinyal untuk data yang tidak pernah tersimpan.
// Kegagalan mengirim sinyal tidak pernah menggagalkan kejadiannya: notifikasi tetap tampil saat daftar dimuat ulang.
@Service
public class NotificationPublisher {

	private static final int MAX_TITLE = 200;
	private static final int MAX_MESSAGE = 1000;

	@Autowired
	private NotificationMessageRepository messageRepository;

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private NotificationPreferenceService preferenceService;

	@Transactional
	public void notify(Collection<String> userIds, String type, String title, String message, String link) {
		Set<String> recipients = new LinkedHashSet<>();
		if (userIds != null) {
			userIds.stream().filter(id -> id != null && !id.isBlank()).forEach(recipients::add);
		}
		// User yang mematikan kategori ini tidak dibuatkan notifikasi sama sekali (bukan hanya disembunyikan).
		recipients.removeAll(preferenceService.mutedFor(NotificationCatalog.categoryOf(type), recipients));
		if (recipients.isEmpty()) {
			return;
		}
		NotificationMessageEntity content = new NotificationMessageEntity(type, cut(title, MAX_TITLE), cut(message, MAX_MESSAGE),
				link);
		messageRepository.store(content);
		for (String userId : recipients) {
			notificationRepository.store(new NotificationEntity(userId, content));
			// Penghitung dibuat bila belum ada, lalu naik satu secara atomik.
			entityManager.createNativeQuery("INSERT INTO public.notification_unread (user_id, total, modificated, registered) "
					+ "VALUES (:user, 1, now(), now()) ON CONFLICT (user_id) DO UPDATE "
					+ "SET total = public.notification_unread.total + 1, modificated = now()")
					.setParameter("user", userId).executeUpdate();
		}
		push(new ArrayList<>(recipients));
	}

	private void push(List<String> userIds) {
		Runnable send = () -> {
			try {
				MessagePostProcessor expire = m -> {
					m.getMessageProperties().setExpiration("120000");
					return m;
				};
				rabbitTemplate.convertAndSend(RabbitMQConfiguration.EXCHANGE_NAME, RabbitMQConfiguration.ROUTING_KEY,
						Map.of("userIds", userIds), expire);
			} catch (Exception e) {
				Log.error(e.getMessage(), e);
			}
		};
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					send.run();
				}
			});
		} else {
			send.run();
		}
	}

	private String cut(String value, int max) {
		String text = value == null ? "" : value.strip();
		return text.length() <= max ? text : text.substring(0, max - 1) + "…";
	}

}
