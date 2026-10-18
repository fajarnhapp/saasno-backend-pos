package com.saasno.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.service.BackupService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.backup.BackupDTO;
import com.saasno.utility.model.ResponseModel;

@RestController
@RequestMapping(Path.API_BACKUP)
public class BackupController {

	@Autowired
	private BackupService backupService;

	@GetMapping
	public ResponseModel<BackupDTO.Overview> overview() {
		return backupService.overview();
	}

	@PostMapping
	public ResponseModel<BackupDTO> run() {
		return backupService.runNow();
	}

	@GetMapping("/download")
	public ResponseEntity<Resource> download(@RequestParam("id") String id) {
		Resource file = backupService.file(id);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"")
				.contentType(MediaType.APPLICATION_OCTET_STREAM)
				.body(file);
	}

}
