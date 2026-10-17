package com.saasno.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.service.TwoFactorAuthService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.user.TwoFactorAuthDTO;
import com.saasno.utility.model.ResponseModel;

@RestController
@RequestMapping(Path.API_USER_OTP)
public class TwoFactorAuthController {

	@Autowired
	private TwoFactorAuthService twoFactorAuthService;

	@PostMapping("/setup")
	public ResponseModel<TwoFactorAuthDTO> setup() {
		return twoFactorAuthService.setup();
	}

	@PostMapping("/enable")
	public ResponseModel<Void> enable(@RequestParam("code") String code) {
		return twoFactorAuthService.enable(code);
	}

	@DeleteMapping
	public ResponseModel<Void> disable(@RequestParam("code") String code) {
		return twoFactorAuthService.disable(code);
	}

}
