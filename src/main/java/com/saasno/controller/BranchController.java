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

import com.saasno.service.BranchService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.branch.BranchDTO;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping(Path.API_BRANCH)
public class BranchController {

	@Autowired
	private BranchService branchService;

	@PostMapping
	public ResponseModel<BranchDTO> create(@Valid @RequestBody BranchDTO branch) {
		return branchService.create(branch);
	}

	@GetMapping
	public ResponseEntity<DataResult<BranchDTO>> read(HttpServletRequest request) {
		return branchService.read(request);
	}

	@PutMapping
	public ResponseModel<BranchDTO> update(@RequestBody BranchDTO branch) {
		return branchService.update(branch);
	}

	@DeleteMapping
	public ResponseModel<BranchDTO> delete(@RequestParam("id") String id) {
		return branchService.delete(id);
	}

}
