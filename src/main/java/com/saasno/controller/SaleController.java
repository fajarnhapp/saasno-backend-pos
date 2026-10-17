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

import com.saasno.service.SaleReportService;
import com.saasno.service.SaleService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.sale.SaleCashDTO;
import com.saasno.utility.dto.sale.SaleDTO;
import com.saasno.utility.dto.sale.SaleRequestDTO;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping(Path.API_SALE)
public class SaleController {

	@Autowired
	private SaleService saleService;

	@Autowired
	private SaleReportService reportService;

	@PostMapping("/preview")
	public ResponseModel<SaleDTO> preview(@RequestBody SaleRequestDTO request) {
		return saleService.preview(request);
	}

	// Transaksi tanpa diskon manual.
	@PostMapping
	public ResponseModel<SaleDTO> create(@RequestBody SaleRequestDTO request) {
		return saleService.create(request, false);
	}

	// Transaksi yang boleh memuat diskon manual; izinnya dipisah lewat role_api.
	@PostMapping("/discount")
	public ResponseModel<SaleDTO> createWithDiscount(@RequestBody SaleRequestDTO request) {
		return saleService.create(request, true);
	}

	@GetMapping
	public ResponseEntity<DataResult<SaleDTO>> read(HttpServletRequest request) {
		return saleService.read(request);
	}

	// Laporan kas harian per cabang; tanggal opsional (default hari ini), format yyyy-MM-dd.
	@GetMapping("/cash")
	public ResponseModel<List<SaleCashDTO>> cash(
			@RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(value = "branchId", required = false) String branchId) {
		return reportService.cash(from, to, branchId);
	}

	@PutMapping("/approve")
	public ResponseModel<SaleDTO> approve(@RequestParam("id") String id,
			@RequestParam(value = "note", required = false) String note) {
		return saleService.approve(id, note);
	}

	@PutMapping("/reject")
	public ResponseModel<SaleDTO> reject(@RequestParam("id") String id,
			@RequestParam(value = "note", required = false) String note) {
		return saleService.reject(id, note);
	}

	@PutMapping("/cancel")
	public ResponseModel<SaleDTO> cancel(@RequestParam("id") String id) {
		return saleService.cancel(id);
	}

	@PutMapping("/void")
	public ResponseModel<SaleDTO> voidSale(@RequestParam("id") String id,
			@RequestParam(value = "note", required = false) String reason) {
		return saleService.voidSale(id, reason);
	}

}
