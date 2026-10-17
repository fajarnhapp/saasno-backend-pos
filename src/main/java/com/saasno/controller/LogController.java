package com.saasno.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.service.LogService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.log.LogDTO;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping(Path.API_LOG)
public class LogController {

	@Autowired
	private LogService logService;

	@GetMapping
	public ResponseEntity<DataResult<LogDTO>> read(HttpServletRequest request) {
		return logService.read(request);
	}

}
