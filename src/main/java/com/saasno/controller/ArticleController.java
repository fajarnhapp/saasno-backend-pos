package com.saasno.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.service.ArticleService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.article.ArticleDTO;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping(Path.API_ARTICLE)
public class ArticleController {

	@Autowired
	private ArticleService articleService;

	@GetMapping
	public ResponseEntity<DataResult<ArticleDTO>> read(HttpServletRequest request) {
		return articleService.read(request);
	}

	// Izin menulis user untuk menampilkan tombol di halaman Artikel.
	@GetMapping("/access")
	public ResponseModel<Map<String, Boolean>> access() {
		return articleService.access();
	}

	@PostMapping
	public ResponseModel<ArticleDTO> create(@RequestBody ArticleDTO request) {
		return articleService.create(request);
	}

	@PutMapping
	public ResponseModel<ArticleDTO> update(@RequestBody ArticleDTO request) {
		return articleService.update(request);
	}

	@DeleteMapping
	public ResponseModel<Void> delete(@RequestParam("id") String id) {
		return articleService.delete(id);
	}

}
