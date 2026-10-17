package com.saasno.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.service.FinanceReportService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.finance.FinanceReportDTO;
import com.saasno.utility.model.ResponseModel;

@RestController
@RequestMapping(Path.API_FINANCE)
public class FinanceController {

	@Autowired
	private FinanceReportService reportService;

	// Laporan keuangan harian per cabang; tanggal opsional (default hari ini), format yyyy-MM-dd.
	@GetMapping("/report")
	public ResponseModel<List<FinanceReportDTO>> report(
			@RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(value = "branchId", required = false) String branchId) {
		return reportService.report(from, to, branchId);
	}

}
