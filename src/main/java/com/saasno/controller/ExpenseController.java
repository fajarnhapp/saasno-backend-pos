package com.saasno.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.service.ExpenseService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.finance.ExpenseDTO;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping(Path.API_EXPENSE)
public class ExpenseController {

	@Autowired
	private ExpenseService expenseService;

	@PostMapping
	public ResponseModel<ExpenseDTO> create(@RequestBody ExpenseDTO request) {
		return expenseService.createCash(request);
	}

	@GetMapping
	public ResponseEntity<DataResult<ExpenseDTO>> read(HttpServletRequest request) {
		return expenseService.read(request);
	}

	@GetMapping("/category")
	public ResponseModel<Map<String, Object>> options() {
		return expenseService.options();
	}

	@PutMapping("/void")
	public ResponseModel<ExpenseDTO> voidExpense(@RequestParam("id") String id,
			@RequestParam(value = "note", required = false) String reason) {
		return expenseService.voidExpense(id, reason);
	}

}
