package com.saasno.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.service.PriceHistoryService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.product.PriceHistoryDTO;
import com.saasno.utility.model.data.DataResult;

@RestController
@RequestMapping(Path.API_PRODUCT + "/price-history")
public class PriceHistoryController {

	@Autowired
	private PriceHistoryService priceHistoryService;

	@GetMapping
	public ResponseEntity<DataResult<PriceHistoryDTO>> read(@RequestParam("productId") String productId,
			@RequestParam(value = "offset", required = false) Integer offset, @RequestParam(value = "limit", required = false) Integer limit) {
		return priceHistoryService.read(productId, offset, limit);
	}

}
