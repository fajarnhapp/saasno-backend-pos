package com.saasno.controller;

import java.util.List;

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

import com.saasno.service.LoyaltyService;
import com.saasno.service.VoucherService;
import com.saasno.utility.dto.loyalty.LoyaltyDTO;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataResult;

@RestController
public class LoyaltyController {

	@Autowired
	private LoyaltyService loyalty;

	@Autowired
	private VoucherService vouchers;

	// ---- poin

	@GetMapping("/api/loyalty")
	public ResponseEntity<DataResult<LoyaltyDTO.CustomerPoints>> customers(@RequestParam(value = "name", required = false) String name,
			@RequestParam(value = "offset", required = false) Integer offset, @RequestParam(value = "limit", required = false) Integer limit) {
		return loyalty.customers(name, offset, limit);
	}

	// Saldo satu customer, untuk layar kasir.
	@GetMapping("/api/loyalty/customer")
	public ResponseModel<LoyaltyDTO.CustomerPoints> customer(@RequestParam("customerId") String customerId) {
		return loyalty.customer(customerId);
	}

	@GetMapping("/api/loyalty/ledger")
	public ResponseModel<List<LoyaltyDTO.LedgerEntry>> ledger(@RequestParam("customerId") String customerId) {
		return loyalty.ledger(customerId);
	}

	@PostMapping("/api/loyalty/adjust")
	public ResponseModel<LoyaltyDTO.CustomerPoints> adjust(@RequestBody LoyaltyDTO.AdjustRequest request) {
		return loyalty.adjust(request);
	}

	// ---- voucher

	@GetMapping("/api/voucher")
	public ResponseEntity<DataResult<LoyaltyDTO.Voucher>> readVouchers(@RequestParam(value = "name", required = false) String name,
			@RequestParam(value = "status", required = false) String status, @RequestParam(value = "offset", required = false) Integer offset,
			@RequestParam(value = "limit", required = false) Integer limit) {
		return vouchers.read(name, status, offset, limit);
	}

	@PostMapping("/api/voucher")
	public ResponseModel<LoyaltyDTO.Voucher> createVoucher(@RequestBody LoyaltyDTO.Voucher request) {
		return vouchers.create(request);
	}

	@PutMapping("/api/voucher")
	public ResponseModel<LoyaltyDTO.Voucher> updateVoucher(@RequestBody LoyaltyDTO.Voucher request) {
		return vouchers.update(request);
	}

	@DeleteMapping("/api/voucher")
	public ResponseModel<LoyaltyDTO.Voucher> deleteVoucher(@RequestParam("id") String id) {
		return vouchers.delete(id);
	}

}
