package com.saasno.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.service.StockTransferService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.branch.BranchDTO;
import com.saasno.utility.dto.stock.StockTransferDTO;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping(Path.API_STOCK_TRANSFER)
public class StockTransferController {

	@Autowired
	private StockTransferService transferService;

	@PostMapping
	public ResponseModel<StockTransferDTO> request(@RequestBody StockTransferDTO request) {
		return transferService.request(request);
	}

	@GetMapping
	public ResponseEntity<DataResult<StockTransferDTO>> read(HttpServletRequest request) {
		return transferService.read(request);
	}

	@GetMapping("/branch")
	public ResponseModel<List<BranchDTO>> destinations(
			@RequestParam(value = "sourceBranchId", required = false) String sourceBranchId) {
		return transferService.destinations(sourceBranchId);
	}

	@PutMapping("/approve")
	public ResponseModel<StockTransferDTO> approve(@RequestParam("id") String id,
			@RequestParam(value = "note", required = false) String note) {
		return transferService.approve(id, note);
	}

	@PutMapping("/reject")
	public ResponseModel<StockTransferDTO> reject(@RequestParam("id") String id,
			@RequestParam(value = "note", required = false) String note) {
		return transferService.reject(id, note);
	}

	@PutMapping("/cancel")
	public ResponseModel<StockTransferDTO> cancel(@RequestParam("id") String id) {
		return transferService.cancel(id);
	}

}
