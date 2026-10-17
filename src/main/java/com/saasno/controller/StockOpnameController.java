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

import com.saasno.service.StockOpnameService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.stock.StockOpnameDTO;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping(Path.API_STOCK_OPNAME)
public class StockOpnameController {

	@Autowired
	private StockOpnameService opnameService;

	@PostMapping
	public ResponseModel<StockOpnameDTO> request(@RequestBody StockOpnameDTO request) {
		return opnameService.request(request);
	}

	@GetMapping
	public ResponseEntity<DataResult<StockOpnameDTO>> read(HttpServletRequest request) {
		return opnameService.read(request);
	}

	@PutMapping("/approve")
	public ResponseModel<StockOpnameDTO> approve(@RequestParam("id") String id,
			@RequestParam(value = "note", required = false) String note) {
		return opnameService.approve(id, note);
	}

	@PutMapping("/reject")
	public ResponseModel<StockOpnameDTO> reject(@RequestParam("id") String id,
			@RequestParam(value = "note", required = false) String note) {
		return opnameService.reject(id, note);
	}

	@PutMapping("/cancel")
	public ResponseModel<StockOpnameDTO> cancel(@RequestParam("id") String id) {
		return opnameService.cancel(id);
	}

}
