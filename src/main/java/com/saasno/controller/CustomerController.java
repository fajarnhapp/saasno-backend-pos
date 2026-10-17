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

import com.saasno.service.CustomerService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.customer.CustomerDTO;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping(Path.API_CUSTOMER)
public class CustomerController {

	@Autowired
	private CustomerService customerService;

	@PostMapping
	public ResponseModel<CustomerDTO> create(@Valid @RequestBody CustomerDTO customer) {
		return customerService.create(customer);
	}

	@GetMapping
	public ResponseEntity<DataResult<CustomerDTO>> read(HttpServletRequest request) {
		return customerService.read(request);
	}

	@PutMapping
	public ResponseModel<CustomerDTO> update(@RequestBody CustomerDTO customer) {
		return customerService.update(customer);
	}

	@DeleteMapping
	public ResponseModel<CustomerDTO> delete(@RequestParam("id") String id) {
		return customerService.delete(id);
	}

}
