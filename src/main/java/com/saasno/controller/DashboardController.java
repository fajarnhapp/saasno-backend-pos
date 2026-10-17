package com.saasno.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.service.DashboardService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.dashboard.DashboardDTO;
import com.saasno.utility.model.ResponseModel;

@RestController
@RequestMapping(Path.API_DASHBOARD)
public class DashboardController {

	@Autowired
	private DashboardService dashboardService;

	// Ringkasan beranda; bagian uang hanya terisi untuk user yang punya izin laporan terkait.
	@GetMapping
	public ResponseModel<DashboardDTO> get() {
		return dashboardService.get();
	}

}
