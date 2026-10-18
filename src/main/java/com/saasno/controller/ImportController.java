package com.saasno.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.saasno.service.ImportService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.dataimport.ImportResultDTO;
import com.saasno.utility.model.ResponseModel;

@RestController
@RequestMapping(Path.API_IMPORT)
public class ImportController {

	@Autowired
	private ImportService importService;

	// Templat berkas Excel untuk satu jenis impor (PRODUCT, PRICE, STOCK).
	@GetMapping("/template")
	public ResponseEntity<byte[]> template(@RequestParam("type") String type) {
		byte[] file = importService.template(type);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"templat-" + type.toLowerCase().replaceAll("[^a-z]", "") + ".xlsx\"")
				.contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
				.body(file);
	}

	// commit=false hanya memeriksa (tidak menyimpan apa pun); commit=true menyimpan bila tidak ada baris yang galat.
	@PostMapping
	public ResponseModel<ImportResultDTO> process(@RequestParam("type") String type, @RequestParam("file") MultipartFile file,
			@RequestParam(value = "commit", defaultValue = "false") boolean commit) {
		return importService.process(type, file, commit);
	}

}
