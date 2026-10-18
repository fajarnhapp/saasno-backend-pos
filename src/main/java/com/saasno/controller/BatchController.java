package com.saasno.controller;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.service.BatchDisposalService;
import com.saasno.service.BatchService;
import com.saasno.service.BranchScopeService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.batch.BatchDTO;
import com.saasno.utility.dto.finance.ExpenseDTO;
import com.saasno.utility.model.ResponseModel;

@RestController
@RequestMapping(Path.API_BATCH)
public class BatchController {

	@Autowired
	private BatchService batchService;

	@Autowired
	private BatchDisposalService disposalService;

	@Autowired
	private BranchScopeService branchScopeService;

	// Daftar batch (urut kedaluwarsa terdekat); status ALL | EXPIRED | SOON.
	@GetMapping
	public ResponseModel<BatchDTO.Page> read(
			@RequestParam(value = "status", required = false) String status,
			@RequestParam(value = "name", required = false) String name,
			@RequestParam(value = "branchId", required = false) String branchId,
			@RequestParam(value = "offset", required = false) Integer offset,
			@RequestParam(value = "limit", required = false) Integer limit) {
		return new ResponseModel<BatchDTO.Page>().success(batchService.read(status, name, branchId, offset, limit));
	}

	@GetMapping("/summary")
	public ResponseModel<BatchDTO.Summary> summary(@RequestParam(value = "branchId", required = false) String branchId) {
		return new ResponseModel<BatchDTO.Summary>().success(batchService.summary(branchScopeService.current(), branchId));
	}

	public record DisposeRequest(String id, BigDecimal quantity, String reason) {
	}

	@PostMapping("/dispose")
	public ResponseModel<ExpenseDTO> dispose(@RequestBody DisposeRequest request) {
		return disposalService.dispose(request.id(), request.quantity(), request.reason());
	}

}
