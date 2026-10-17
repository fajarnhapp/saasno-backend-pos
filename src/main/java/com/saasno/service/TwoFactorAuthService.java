package com.saasno.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.saasno.entity.UserEntity;
import com.saasno.repository.UserRepository;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.cryptography.Totp;
import com.saasno.utility.dto.user.TwoFactorAuthDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.verification.StringVerification;

import jakarta.transaction.Transactional;

@Service
public class TwoFactorAuthService {

	private static final String ISSUER = "Saasno";

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private SessionUtility sessionUtility;

	@Transactional
	public ResponseModel<TwoFactorAuthDTO> setup() {
		UserEntity user = currentUser();
		if (Boolean.TRUE.equals(user.getTwoFactorAuth())) {
			throw new ApiException("409", "Autentikasi dua faktor sudah aktif");
		}

		String secret = Totp.generateSecret();
		user.setTwoFactorAuthSecret(secret);
		userRepository.store(user);

		String setupUri = Totp.buildUri(ISSUER, user.getUsername(), secret);
		return new ResponseModel<TwoFactorAuthDTO>().success(new TwoFactorAuthDTO(setupUri));
	}

	@Transactional
	public ResponseModel<Void> enable(String code) {
		UserEntity user = currentUser();
		if (StringVerification.nullOrEmpty(user.getTwoFactorAuthSecret())) {
			throw new ApiException("400", "Mulai proses pengaturan OTP terlebih dahulu");
		}
		if (!Totp.verify(user.getTwoFactorAuthSecret(), code)) {
			throw new ApiException("400", "Kode OTP tidak valid");
		}

		user.setTwoFactorAuth(true);
		userRepository.store(user);
		return new ResponseModel<Void>().success();
	}

	@Transactional
	public ResponseModel<Void> disable(String code) {
		UserEntity user = currentUser();
		if (!Boolean.TRUE.equals(user.getTwoFactorAuth())) {
			throw new ApiException("409", "Autentikasi dua faktor belum aktif");
		}
		if (!Totp.verify(user.getTwoFactorAuthSecret(), code)) {
			throw new ApiException("400", "Kode OTP tidak valid");
		}

		user.setTwoFactorAuth(false);
		user.setTwoFactorAuthSecret(null);
		userRepository.store(user);
		return new ResponseModel<Void>().success();
	}

	private UserEntity currentUser() {
		UserEntity user = userRepository.findByUsername(sessionUtility.getUsername());
		if (user == null) {
			throw new ApiException("404", "User tidak ditemukan");
		}
		return user;
	}

}
