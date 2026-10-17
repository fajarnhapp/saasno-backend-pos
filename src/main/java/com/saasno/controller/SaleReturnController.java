package com.saasno.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.service.SaleReturnService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.sale.SaleReturnDTO;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping(Path.API_SALE_RETURN)
public class SaleReturnController {

	@Autowired
	private SaleReturnService returnService;

	@PostMapping
	public ResponseModel<SaleReturnDTO> create(@RequestBody SaleReturnDTO request) {
		return returnService.create(request);
	}

	@PostMapping("/preview")
	public ResponseModel<SaleReturnDTO> preview(@RequestBody SaleReturnDTO request) {
		return returnService.preview(request);
	}

	@GetMapping
	public ResponseEntity<DataResult<SaleReturnDTO>> read(HttpServletRequest request) {
		return returnService.read(request);
	}

}
