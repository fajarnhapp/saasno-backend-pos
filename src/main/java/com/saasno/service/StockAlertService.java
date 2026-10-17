package com.saasno.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.saasno.entity.ProductEntity;
import com.saasno.repository.ConfigurationRepository;
import com.saasno.utility.constant.ConfigurationKey;

// Memberi tahu penerima barang (izin POST_API_STOCK_IN) di cabang itu saat stok sebuah produk MELEWATI batas, bukan
// setiap kali stok sedang rendah: sekali saat turun ke batas menipis (LOW_STOCK_THRESHOLD) atau lebih rendah, dan sekali
// lagi saat habis. Stok yang sudah di bawah batas lalu berkurang lagi tidak mengirim apa pun.
@Service
public class StockAlertService {

	private static final long DEFAULT_THRESHOLD = 5;

	@Autowired
	private NotificationPublisher notificationPublisher;

	@Autowired
	private NotificationRecipients notificationRecipients;

	@Autowired
	private BranchScopeService branchScopeService;

	@Autowired
	private ConfigurationRepository configurationRepository;

	// before dan after dalam satuan dasar; actorId tidak diberi tahu karena dia sendiri yang menyebabkannya.
	public void onDecrease(String branchId, ProductEntity product, BigDecimal before, BigDecimal after, String actorId) {
		BigDecimal threshold = BigDecimal.valueOf(threshold());
		boolean out = before.signum() > 0 && after.signum() <= 0;
		boolean low = !out && before.compareTo(threshold) > 0 && after.compareTo(threshold) <= 0;
		if (!out && !low) {
			return;
		}
		Set<String> recipients = notificationRecipients.withApi("POST_API_STOCK_IN", branchId);
		recipients.remove(actorId);
		String branch = branchScopeService.names(List.of(branchId)).get(branchId);
		String name = product.getSerial() + " " + product.getName();
		notificationPublisher.notify(recipients, out ? "STOCK_OUT" : "STOCK_LOW",
				out ? "Stok habis" : "Stok menipis",
				out ? name + " habis di " + branch + "."
						: name + " tinggal " + after.stripTrailingZeros().toPlainString() + " di " + branch + " (batas "
								+ threshold.toPlainString() + ").",
				"/stock/balance");
	}

	private long threshold() {
		String value = configurationRepository.get(ConfigurationKey.LOW_STOCK_THRESHOLD);
		try {
			return value == null ? DEFAULT_THRESHOLD : Long.parseLong(value.trim());
		} catch (NumberFormatException e) {
			return DEFAULT_THRESHOLD;
		}
	}

}
