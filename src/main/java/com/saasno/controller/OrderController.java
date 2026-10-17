package com.saasno.controller;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.service.OnlineOrderService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.order.OrderDTO;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping(Path.API_ORDER)
public class OrderController {

	@Autowired
	private OnlineOrderService orderService;

	@GetMapping
	public ResponseEntity<DataResult<OrderDTO>> read(HttpServletRequest request) {
		return orderService.read(request);
	}

	@PutMapping("/approve")
	public ResponseModel<OrderDTO> approve(@RequestParam("id") String id, @RequestParam(value = "note", required = false) String note) {
		return orderService.approve(id, note);
	}

	@PutMapping("/reject")
	public ResponseModel<OrderDTO> reject(@RequestParam("id") String id, @RequestParam(value = "note", required = false) String note) {
		return orderService.reject(id, note);
	}

	@PutMapping("/ready")
	public ResponseModel<OrderDTO> ready(@RequestParam("id") String id, @RequestParam(value = "note", required = false) String note) {
		return orderService.ready(id, note);
	}

	@PutMapping("/complete")
	public ResponseModel<OrderDTO> complete(@RequestParam("id") String id, @RequestParam(value = "paid", required = false) BigDecimal paid) {
		return orderService.complete(id, paid);
	}

	@PutMapping("/cancel")
	public ResponseModel<OrderDTO> cancel(@RequestParam("id") String id, @RequestParam(value = "note", required = false) String note) {
		return orderService.cancel(id, note);
	}

}
