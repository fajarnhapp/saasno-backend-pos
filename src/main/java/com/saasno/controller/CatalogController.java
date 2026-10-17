package com.saasno.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.saasno.service.CatalogService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.catalog.CatalogProductDTO;
import com.saasno.utility.dto.catalog.CatalogSettingDTO;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping(Path.API_CATALOG)
public class CatalogController {

	@Autowired
	private CatalogService catalogService;

	@GetMapping("/setting")
	public ResponseModel<CatalogSettingDTO> setting(@RequestParam(value = "branchId", required = false) String branchId) {
		return catalogService.setting(branchId);
	}

	@PutMapping("/setting")
	public ResponseModel<CatalogSettingDTO> saveSetting(@RequestBody CatalogSettingDTO request) {
		return catalogService.saveSetting(request);
	}

	@GetMapping("/product")
	public ResponseEntity<DataResult<CatalogProductDTO>> products(HttpServletRequest request) {
		return catalogService.products(request);
	}

	@PutMapping("/product")
	public ResponseModel<CatalogProductDTO> saveProduct(@RequestBody CatalogProductDTO request) {
		return catalogService.saveProduct(request);
	}

	@PostMapping("/product/image")
	public ResponseModel<CatalogProductDTO> uploadImage(@RequestParam("id") String id, @RequestParam("file") MultipartFile file) {
		return catalogService.uploadImage(id, file);
	}

	@GetMapping("/product/image")
	public ResponseEntity<byte[]> image(@RequestParam("id") String id) {
		byte[] bytes = catalogService.image(id);
		if (bytes == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).header("Cache-Control", "private, no-cache")
				.header("X-Content-Type-Options", "nosniff").body(bytes);
	}

	@DeleteMapping("/product/image")
	public ResponseModel<CatalogProductDTO> deleteImage(@RequestParam("id") String id) {
		return catalogService.deleteImage(id);
	}

}
