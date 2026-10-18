package com.saasno.controller;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.service.ShiftService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.shift.ShiftDTO;
import com.saasno.utility.dto.shift.ShiftRequestDTO;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataResult;

@RestController
@RequestMapping(Path.API_SHIFT)
public class ShiftController {

	@Autowired
	private ShiftService shiftService;

	@GetMapping("/current")
	public ResponseModel<ShiftDTO.State> current() {
		return shiftService.current();
	}

	@PostMapping("/open")
	public ResponseModel<ShiftDTO> open(@RequestBody ShiftRequestDTO request) {
		return shiftService.open(request);
	}

	@PostMapping("/close")
	public ResponseModel<ShiftDTO> close(@RequestBody ShiftRequestDTO request) {
		return shiftService.close(request);
	}

	@PostMapping("/force-close")
	public ResponseModel<ShiftDTO> forceClose(@RequestBody ShiftRequestDTO request) {
		return shiftService.forceClose(request);
	}

	@GetMapping("/summary")
	public ResponseModel<ShiftDTO> summary(@RequestParam("id") String id) {
		return shiftService.summary(id);
	}

	// Riwayat shift sendiri.
	@GetMapping("/mine")
	public ResponseEntity<DataResult<ShiftDTO>> mine(
			@RequestParam(value = "status", required = false) String status,
			@RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(value = "offset", required = false) Integer offset,
			@RequestParam(value = "limit", required = false) Integer limit) {
		return shiftService.mine(status, from, to, offset, limit);
	}

	// Shift semua kasir dalam cakupan cabang.
	@GetMapping
	public ResponseEntity<DataResult<ShiftDTO>> all(
			@RequestParam(value = "branchId", required = false) String branchId,
			@RequestParam(value = "userId", required = false) String userId,
			@RequestParam(value = "status", required = false) String status,
			@RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(value = "offset", required = false) Integer offset,
			@RequestParam(value = "limit", required = false) Integer limit) {
		return shiftService.all(branchId, userId, status, from, to, offset, limit);
	}

}
