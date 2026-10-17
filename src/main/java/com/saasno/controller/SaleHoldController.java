package com.saasno.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.service.SaleHoldService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.sale.SaleHoldDTO;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping(Path.API_SALE_HOLD)
public class SaleHoldController {

	@Autowired
	private SaleHoldService holdService;

	@PostMapping
	public ResponseModel<SaleHoldDTO> save(@RequestBody SaleHoldDTO request) {
		return holdService.save(request);
	}

	@GetMapping
	public ResponseEntity<DataResult<SaleHoldDTO>> read(HttpServletRequest request) {
		return holdService.read(request);
	}

	@PutMapping("/discard")
	public ResponseModel<SaleHoldDTO> discard(@RequestParam("id") String id,
			@RequestParam(value = "note", required = false) String reason) {
		return holdService.discard(id, reason);
	}

}
