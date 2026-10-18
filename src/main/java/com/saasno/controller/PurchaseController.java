package com.saasno.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.service.PurchaseService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.purchase.PurchaseDTO;
import com.saasno.utility.dto.purchase.PurchaseRequestDTO;
import com.saasno.utility.dto.purchase.SuggestionDTO;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataResult;

@RestController
@RequestMapping(Path.API_PURCHASE)
public class PurchaseController {

	@Autowired
	private PurchaseService purchaseService;

	@GetMapping
	public ResponseEntity<DataResult<PurchaseDTO>> read(
			@RequestParam(value = "serial", required = false) String serial,
			@RequestParam(value = "status", required = false) String status,
			@RequestParam(value = "supplierId", required = false) String supplierId,
			@RequestParam(value = "branchId", required = false) String branchId,
			@RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(value = "offset", required = false) Integer offset,
			@RequestParam(value = "limit", required = false) Integer limit) {
		return purchaseService.read(serial, status, supplierId, branchId, from, to, offset, limit);
	}

	@GetMapping("/detail")
	public ResponseModel<PurchaseDTO> detail(@RequestParam("id") String id) {
		return purchaseService.get(id);
	}

	@GetMapping("/suggestion")
	public ResponseModel<List<SuggestionDTO>> suggestion(@RequestParam(value = "branchId", required = false) String branchId) {
		return purchaseService.suggestion(branchId);
	}

	@PostMapping
	public ResponseModel<PurchaseDTO> create(@RequestBody PurchaseRequestDTO request) {
		return purchaseService.create(request);
	}

	@PutMapping
	public ResponseModel<PurchaseDTO> update(@RequestBody PurchaseRequestDTO request) {
		return purchaseService.update(request);
	}

	@PostMapping("/order")
	public ResponseModel<PurchaseDTO> order(@RequestBody PurchaseRequestDTO request) {
		return purchaseService.order(request);
	}

	@PostMapping("/receive")
	public ResponseModel<PurchaseDTO> receive(@RequestBody PurchaseRequestDTO request) {
		return purchaseService.receive(request);
	}

	@PostMapping("/close")
	public ResponseModel<PurchaseDTO> close(@RequestBody PurchaseRequestDTO request) {
		return purchaseService.close(request);
	}

	@PostMapping("/cancel")
	public ResponseModel<PurchaseDTO> cancel(@RequestBody PurchaseRequestDTO request) {
		return purchaseService.cancel(request);
	}

}
