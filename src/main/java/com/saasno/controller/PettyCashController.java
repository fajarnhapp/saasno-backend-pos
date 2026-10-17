package com.saasno.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.service.PettyCashService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.finance.PettyCashBalanceDTO;
import com.saasno.utility.dto.finance.PettyCashEntryDTO;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping(Path.API_PETTY_CASH)
public class PettyCashController {

	@Autowired
	private PettyCashService pettyCashService;

	@PostMapping("/topup")
	public ResponseModel<PettyCashEntryDTO> topUp(@RequestBody PettyCashEntryDTO request) {
		return pettyCashService.topUp(request);
	}

	@GetMapping
	public ResponseEntity<DataResult<PettyCashEntryDTO>> read(HttpServletRequest request) {
		return pettyCashService.read(request);
	}

	@GetMapping("/balance")
	public ResponseModel<List<PettyCashBalanceDTO>> balances() {
		return pettyCashService.balances();
	}

}
