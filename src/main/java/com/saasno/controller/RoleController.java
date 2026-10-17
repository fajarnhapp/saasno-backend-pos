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

import com.saasno.service.RoleService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.role.RoleDTO;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping(Path.API_ROLE)
public class RoleController {

	@Autowired
	private RoleService roleService;

	@PostMapping
	public ResponseModel<RoleDTO> create(@Valid @RequestBody RoleDTO role) {
		return roleService.create(role);
	}

	@GetMapping
	public ResponseEntity<DataResult<RoleDTO>> read(HttpServletRequest request) {
		return roleService.read(request);
	}

	@PutMapping
	public ResponseModel<RoleDTO> update(@RequestBody RoleDTO role) {
		return roleService.update(role);
	}

	@DeleteMapping
	public ResponseModel<RoleDTO> delete(@RequestParam("id") String id) {
		return roleService.delete(id);
	}

}
