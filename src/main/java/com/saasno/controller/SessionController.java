package com.saasno.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.service.SessionManagementService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.session.SessionDTO;
import com.saasno.utility.model.ResponseModel;

@RestController
@RequestMapping(Path.API_SESSION)
public class SessionController {

	@Autowired
	private SessionManagementService sessions;

	// Perangkat sendiri.
	@GetMapping
	public ResponseModel<List<SessionDTO>> mine() {
		return sessions.mine();
	}

	// Mengeluarkan satu perangkat sendiri (handle), atau semua perangkat lain bila handle kosong.
	@DeleteMapping
	public Object revokeMine(@RequestParam(value = "handle", required = false) String handle) {
		return handle == null || handle.isBlank() ? sessions.revokeOthers() : sessions.revokeMine(handle);
	}

	// Sesi semua user dalam cakupan cabang.
	@GetMapping("/all")
	public ResponseModel<List<SessionDTO>> all() {
		return sessions.all();
	}

	@DeleteMapping("/all")
	public ResponseModel<Void> revokeAny(@RequestParam("handle") String handle) {
		return sessions.revokeAny(handle);
	}

}
