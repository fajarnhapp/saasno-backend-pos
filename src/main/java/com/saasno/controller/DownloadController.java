package com.saasno.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.service.DownloadJobService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.download.DownloadJobDTO;
import com.saasno.utility.dto.download.DownloadJobRequestDTO;
import com.saasno.utility.model.ResponseModel;

import jakarta.validation.Valid;

@RestController
@RequestMapping(Path.API_DOWNLOAD)
public class DownloadController {

	@Autowired
	private DownloadJobService downloadJobService;

	@PostMapping
	public ResponseModel<DownloadJobDTO> create(@Valid @RequestBody DownloadJobRequestDTO request) {
		ResponseModel<DownloadJobDTO> response = downloadJobService.create(request);
		downloadJobService.publish(response.getData().getId());
		return response;
	}

	@GetMapping
	public ResponseModel<List<DownloadJobDTO>> read() {
		return downloadJobService.readMine();
	}

	@GetMapping("/file")
	public ResponseEntity<Resource> file(@RequestParam("id") String id) {
		return downloadJobService.file(id);
	}

	@DeleteMapping
	public ResponseModel<Void> delete(@RequestParam("id") String id) {
		return downloadJobService.delete(id);
	}

}
