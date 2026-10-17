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

import com.saasno.service.MenuService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.menu.MenuDTO;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping(Path.API_MENU)
public class MenuController {

	@Autowired
	private MenuService menuService;

	@PostMapping
	public ResponseModel<MenuDTO> create(@Valid @RequestBody MenuDTO position) {
		return menuService.create(position);
	}

	@GetMapping
	public ResponseEntity<DataResult<MenuDTO>> read(HttpServletRequest request) {
		return menuService.read(request);
	}

	@PutMapping
	public ResponseModel<MenuDTO> update(@RequestBody MenuDTO position) {
		return menuService.update(position);
	}

	@DeleteMapping
	public ResponseModel<MenuDTO> delete(@RequestParam("id") String id) {
		return menuService.delete(id);
	}

}
