package com.saasno.consumer;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.saasno.service.DownloadJobService;
import com.saasno.utility.logger.Log;

@Component
public class DownloadConsumer {

	@Autowired
	private DownloadJobService downloadJobService;

	// concurrency = "1" -> hanya satu job download yang diproses dalam satu waktu; job lain
	// yang di-publish selagi ini berjalan otomatis menunggu di antrian RabbitMQ sampai giliran.
	@RabbitListener(queues = "download-queue", concurrency = "1")
	public void receive(String jobId) {
		downloadJobService.markProcessing(jobId);
		try {
			downloadJobService.exportAndComplete(jobId);
		} catch (Exception e) {
			Log.error("Failed to process download job " + jobId, e);
			downloadJobService.markFailed(jobId, e.getMessage());
		}
	}

}
