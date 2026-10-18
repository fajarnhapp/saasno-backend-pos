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

import com.saasno.service.SupplierService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.purchase.SupplierDTO;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataResult;

@RestController
@RequestMapping(Path.API_SUPPLIER)
public class SupplierController {

	@Autowired
	private SupplierService supplierService;

	@GetMapping
	public ResponseEntity<DataResult<SupplierDTO>> read(
			@RequestParam(value = "name", required = false) String name,
			@RequestParam(value = "status", required = false) String status,
			@RequestParam(value = "branchId", required = false) String branchId,
			@RequestParam(value = "offset", required = false) Integer offset,
			@RequestParam(value = "limit", required = false) Integer limit) {
		return supplierService.read(name, status, branchId, offset, limit);
	}

	@PostMapping
	public ResponseModel<SupplierDTO> create(@RequestBody SupplierDTO request) {
		return supplierService.create(request);
	}

	@PutMapping
	public ResponseModel<SupplierDTO> update(@RequestBody SupplierDTO request) {
		return supplierService.update(request);
	}

	@DeleteMapping
	public ResponseModel<SupplierDTO> delete(@RequestParam("id") String id) {
		return supplierService.delete(id);
	}

}
