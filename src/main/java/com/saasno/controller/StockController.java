package com.saasno.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.service.ExpenseService;
import com.saasno.service.StockService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.finance.ExpenseDTO;
import com.saasno.utility.dto.stock.StockChangeDTO;
import com.saasno.utility.dto.stock.StockDTO;
import com.saasno.utility.dto.stock.StockMovementDTO;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping(Path.API_STOCK)
public class StockController {

	@Autowired
	private StockService stockService;

	@Autowired
	private ExpenseService expenseService;

	@GetMapping
	public ResponseEntity<DataResult<StockDTO>> read(HttpServletRequest request) {
		return stockService.read(request);
	}

	@GetMapping("/movement")
	public ResponseEntity<DataResult<StockMovementDTO>> movements(HttpServletRequest request) {
		return stockService.readMovements(request);
	}

	@PostMapping("/in")
	public ResponseModel<StockDTO> receive(@RequestBody StockChangeDTO request) {
		return stockService.receive(request);
	}

	// Memakai stok sendiri oleh toko (biaya operasional): stok berkurang, nilainya jumlah x harga pokok.
	@PostMapping("/use")
	public ResponseModel<ExpenseDTO> use(@RequestBody ExpenseDTO request) {
		return expenseService.useStock(request);
	}

	@PostMapping("/adjust")
	public ResponseModel<StockDTO> adjust(@RequestBody StockChangeDTO request) {
		return stockService.adjust(request);
	}

}
