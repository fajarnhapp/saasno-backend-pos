package com.saasno.controller;

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

import com.saasno.service.ApiService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.api.ApiDTO;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping(Path.API_API)
public class ApiController {

	@Autowired
	private ApiService apiService;

	@PostMapping
	public ResponseModel<ApiDTO> create(@Valid @RequestBody ApiDTO api) {
		return apiService.create(api);
	}

	@GetMapping
	public ResponseEntity<DataResult<ApiDTO>> read(HttpServletRequest request) {
		return apiService.read(request);
	}

	@PutMapping
	public ResponseModel<ApiDTO> update(@RequestBody ApiDTO api) {
		return apiService.update(api);
	}

	@DeleteMapping
	public ResponseModel<ApiDTO> delete(@RequestParam("id") String id) {
		return apiService.delete(id);
	}

}
