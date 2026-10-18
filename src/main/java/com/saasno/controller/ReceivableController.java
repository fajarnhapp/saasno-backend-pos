package com.saasno.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.service.ReceivableService;
import com.saasno.utility.dto.receivable.ReceivableDTO;
import com.saasno.utility.model.ResponseModel;

@RestController
@RequestMapping("/api/receivable")
public class ReceivableController {

	@Autowired
	private ReceivableService receivables;

	// status: ALL | OPEN | OVERDUE | PAID | VOID | WRITTEN_OFF
	@GetMapping
	public ResponseModel<ReceivableDTO.Page> read(@RequestParam(value = "status", required = false) String status,
			@RequestParam(value = "name", required = false) String name, @RequestParam(value = "branchId", required = false) String branchId,
			@RequestParam(value = "offset", required = false) Integer offset, @RequestParam(value = "limit", required = false) Integer limit) {
		return new ResponseModel<ReceivableDTO.Page>().success(receivables.read(status, name, branchId, offset, limit));
	}

	@GetMapping("/detail")
	public ResponseModel<ReceivableDTO> detail(@RequestParam("id") String id) {
		return receivables.detail(id);
	}

	@PostMapping("/pay")
	public ResponseModel<ReceivableDTO> pay(@RequestBody ReceivableDTO.PayRequest request) {
		return receivables.pay(request);
	}

	@PostMapping("/writeoff")
	public ResponseModel<ReceivableDTO> writeOff(@RequestBody ReceivableDTO.WriteOffRequest request) {
		return receivables.writeOff(request);
	}

}
