package com.saasno.consumer;

import java.util.List;
import java.util.Map;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.saasno.repository.ConfigurationRepository;
import com.saasno.utility.constant.ConfigurationKey;
import com.saasno.utility.logger.Log;

// Meneruskan sinyal "ada notifikasi baru" ke server socket di frontend, yang mengabari hanya user penerimanya.
// SAASNO_NOTIFICATION_PUSH_KEY (opsional) harus sama dengan yang di frontend; tanpa kunci, frontend hanya menerima
// panggilan dari mesin yang sama.
@Component
public class NotificationConsumer {

	@Autowired
	private ConfigurationRepository configurationRepository;

	@Value("${SAASNO_NOTIFICATION_PUSH_KEY:}")
	private String pushKey;

	@RabbitListener(queues = "notification-queue", concurrency = "1")
	public void receive(Map<String, Object> payload) {
		try {
			Object userIds = payload == null ? null : payload.get("userIds");
			if (!(userIds instanceof List<?> list) || list.isEmpty()) {
				return;
			}
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			if (pushKey != null && !pushKey.isBlank()) {
				headers.set("X-Push-Key", pushKey);
			}
			String base = configurationRepository.get(ConfigurationKey.FRONTEND_URL);
			new RestTemplate().postForObject(base + "/api/service/notification",
					new HttpEntity<>(Map.of("userIds", list), headers), String.class);
		} catch (Exception e) {
			Log.error(e.getMessage(), e);
		}
	}
}
