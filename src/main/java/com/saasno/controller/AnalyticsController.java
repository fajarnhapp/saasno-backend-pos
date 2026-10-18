package com.saasno.controller;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.service.AnalyticsBranchService;
import com.saasno.service.AnalyticsCustomerService;
import com.saasno.service.AnalyticsFinanceService;
import com.saasno.service.AnalyticsInventoryService;
import com.saasno.service.AnalyticsPromoService;
import com.saasno.service.AnalyticsSalesService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.analytics.AnalyticsDTO.Branches;
import com.saasno.utility.dto.analytics.AnalyticsDTO.Customers;
import com.saasno.utility.dto.analytics.AnalyticsDTO.Finance;
import com.saasno.utility.dto.analytics.AnalyticsDTO.Inventory;
import com.saasno.utility.dto.analytics.AnalyticsDTO.Promo;
import com.saasno.utility.dto.analytics.AnalyticsDTO.Sales;
import com.saasno.utility.model.ResponseModel;

// Halaman Analitik. Semua endpoint menerima from/to (yyyy-MM-dd; bawaan 30 hari terakhir, maksimal 366 hari) dan branchId
// (kosong = seluruh cakupan cabang user). Izin per endpoint lewat tabel role_api.
@RestController
@RequestMapping(Path.API_ANALYTICS)
public class AnalyticsController {

	@Autowired
	private AnalyticsFinanceService finance;

	@Autowired
	private AnalyticsInventoryService inventory;

	@Autowired
	private AnalyticsSalesService sales;

	@Autowired
	private AnalyticsPromoService promo;

	@Autowired
	private AnalyticsCustomerService customer;

	@Autowired
	private AnalyticsBranchService branch;

	@GetMapping("/finance")
	public ResponseModel<Finance> finance(
			@RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(value = "branchId", required = false) String branchId) {
		return finance.get(from, to, branchId);
	}

	// deadDays: produk tanpa penjualan selama sekian hari terakhir dianggap stok mati (bawaan 30).
	@GetMapping("/inventory")
	public ResponseModel<Inventory> inventory(
			@RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(value = "branchId", required = false) String branchId,
			@RequestParam(value = "deadDays", required = false) Integer deadDays) {
		return inventory.get(from, to, branchId, deadDays);
	}

	@GetMapping("/sales")
	public ResponseModel<Sales> sales(
			@RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(value = "branchId", required = false) String branchId) {
		return sales.get(from, to, branchId);
	}

	@GetMapping("/promo")
	public ResponseModel<Promo> promo(
			@RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(value = "branchId", required = false) String branchId) {
		return promo.get(from, to, branchId);
	}

	@GetMapping("/customer")
	public ResponseModel<Customers> customer(
			@RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(value = "branchId", required = false) String branchId) {
		return customer.get(from, to, branchId);
	}

	@GetMapping("/branch")
	public ResponseModel<Branches> branch(
			@RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(value = "branchId", required = false) String branchId) {
		return branch.get(from, to, branchId);
	}

}
