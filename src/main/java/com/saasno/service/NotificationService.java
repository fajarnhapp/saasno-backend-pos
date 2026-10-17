package com.saasno.service;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.entity.NotificationEntity;
import com.saasno.filter.ColumnFilter;
import com.saasno.repository.NotificationRepository;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.dto.notification.NotificationDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.logger.Log;
import com.saasno.utility.mapper.NotificationMapper;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class NotificationService {

	// Batas umur notifikasi yang ditampilkan; yang lebih tua tidak diambil dan dibersihkan harian oleh NotificationCleanupJob.
	public static final int MAX_AGE_DAYS = 30;

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private NotificationMapper notificationMapper;

	@Autowired
	private SessionUtility sessionUtility;

	@Transactional(readOnly = true)
	public ResponseEntity<DataResult<NotificationDTO>> read(HttpServletRequest request) {
		try {
			ColumnFilter columnFilterUser = new ColumnFilter();
			columnFilterUser.setColumn("userId");
			columnFilterUser.setOperator("=");
			columnFilterUser.setValue(sessionUtility.getUserId());

			DataFilter dataFilter = new DataFilter(request);
			dataFilter.getColumn().add(columnFilterUser);
			// Hanya notifikasi sebulan terakhir yang pernah diambil, apa pun filter dari klien (tanggal mulai dari klien hanya boleh
			// mempersempit, karena semua filter digabung dengan AND).
			dataFilter.getColumn().add(new ColumnFilter("registered", LocalDateTime.now().minusDays(MAX_AGE_DAYS).toString(), ">="));
			// Tanpa urutan, urutan baris tidak terjamin dan halaman berikutnya bisa mengulang atau melewatkan data: bawaannya terbaru dulu.
			if (dataFilter.getOrder() == null) {
				dataFilter.setOrder("registered");
				dataFilter.setDescending(true);
			}

			DataResult<NotificationEntity> result = notificationRepository.dataTable(dataFilter);
			List<NotificationDTO> dto = notificationMapper.dto(result.getData());
			return new ResponseEntity<DataResult<NotificationDTO>>(
					new DataResult<>(dto, result.getTotal(), result.getGrandTotal()), HttpStatus.OK);
		} catch (Exception e) {
			Log.error(e.getMessage(), e);
			return new ResponseEntity<DataResult<NotificationDTO>>(
					new DataResult<NotificationDTO>(BigInteger.ZERO, BigInteger.ZERO), HttpStatus.OK);
		}
	}

	@Transactional
	public ResponseModel<NotificationDTO> update(NotificationDTO notification) {
		NotificationEntity existingNotification = notificationRepository.find(notification.getId());
		// Hanya notifikasi milik sendiri; milik orang lain dijawab seperti tidak ada.
		if (existingNotification == null || !existingNotification.getUserId().equals(sessionUtility.getUserId())) {
			throw new ApiException("404", "Notifikasi tidak ditemukan");
		}

		existingNotification.setStatus(1);
		notificationRepository.update(existingNotification);
		return new ResponseModel<NotificationDTO>().success(notificationMapper.dto(existingNotification));
	}
}
