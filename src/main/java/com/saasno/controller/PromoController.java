package com.saasno.controller;

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

import com.saasno.service.PromoService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.promo.PromoDTO;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping(Path.API_PROMO)
public class PromoController {

	@Autowired
	private PromoService promoService;

	@PostMapping
	public ResponseModel<PromoDTO> create(@Valid @RequestBody PromoDTO promo) {
		return promoService.create(promo);
	}

	@GetMapping
	public ResponseEntity<DataResult<PromoDTO>> read(HttpServletRequest request) {
		return promoService.read(request);
	}

	@PutMapping
	public ResponseModel<PromoDTO> update(@RequestBody PromoDTO promo) {
		return promoService.update(promo);
	}

	@DeleteMapping
	public ResponseModel<PromoDTO> delete(@RequestParam("id") String id) {
		return promoService.delete(id);
	}

}
