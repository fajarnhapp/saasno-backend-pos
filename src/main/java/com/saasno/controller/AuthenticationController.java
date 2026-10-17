package com.saasno.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.entity.SessionEntity;
import com.saasno.service.AuthenticationService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.model.ResponseModel;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping
public class AuthenticationController {

	@Autowired
	private AuthenticationService authenticationService;

	@PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, value = Path.API_LOGIN)
	public ResponseModel<SessionEntity> authentication(HttpServletRequest request) {
		return authenticationService.authentication(request);
	}

	@DeleteMapping(Path.API_LOGOUT)
	public ResponseModel<SessionEntity> unauthentication(HttpServletRequest request) {
		return authenticationService.unauthenticated(request);
	}

	@PostMapping(Path.API_LOGIN_OTP_VERIFY)
	public ResponseModel<SessionEntity> otpVerify(HttpServletRequest request, @RequestParam("code") String code) {
		return authenticationService.verifyOtp(request, code);
	}

}
