package com.saasno.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.service.RoleMenuService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.role.RoleMenuDTO;
import com.saasno.utility.dto.role.RoleAssignmentResultDTO;
import com.saasno.utility.dto.role.RoleAssignmentUpdateDTO;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping(Path.API_ROLE_MENU)
public class RoleMenuController {

	@Autowired
	private RoleMenuService roleMenuService;

	@PutMapping
	public ResponseModel<RoleAssignmentResultDTO> update(@Valid @RequestBody RoleAssignmentUpdateDTO request) {
		return roleMenuService.update(request);
	}

	@GetMapping
	public ResponseEntity<DataResult<RoleMenuDTO>> read(HttpServletRequest request) {
		return roleMenuService.read(request);
	}

	@GetMapping("/access")
	public ResponseModel<Boolean> checkMenuAccess(@RequestParam("menuId") String menuId) {
		return roleMenuService.canAccessMenu(menuId);
	}

}
