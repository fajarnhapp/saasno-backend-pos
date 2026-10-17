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

import com.saasno.service.ProductService;
import com.saasno.service.ProductUnitService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.product.ProductDTO;
import com.saasno.utility.dto.product.ProductPriceResultDTO;
import com.saasno.utility.dto.product.ProductUnitDTO;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataResult;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping(Path.API_PRODUCT)
public class ProductController {

	@Autowired
	private ProductService productService;

	@Autowired
	private ProductUnitService productUnitService;

	@PostMapping
	public ResponseModel<ProductDTO> create(@Valid @RequestBody ProductDTO product) {
		return productService.create(product);
	}

	@GetMapping
	public ResponseEntity<DataResult<ProductDTO>> read(HttpServletRequest request) {
		return productService.read(request);
	}

	@GetMapping("/unit")
	public ResponseModel<List<ProductUnitDTO>> units() {
		return productUnitService.read();
	}

	@GetMapping("/price")
	public ResponseModel<ProductPriceResultDTO> price(@RequestParam("productId") String productId,
			@RequestParam(value = "customerId", required = false) String customerId,
			@RequestParam(value = "quantity", required = false) java.math.BigDecimal quantity) {
		return productService.price(productId, customerId, quantity);
	}

	@PutMapping
	public ResponseModel<ProductDTO> update(@RequestBody ProductDTO product) {
		return productService.update(product);
	}

	@DeleteMapping
	public ResponseModel<ProductDTO> delete(@RequestParam("id") String id) {
		return productService.delete(id);
	}

}
