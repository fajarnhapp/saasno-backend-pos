package com.saasno.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.saasno.entity.SessionEntity;
import com.saasno.entity.UserEntity;
import com.saasno.repository.ConfigurationRepository;
import com.saasno.repository.UserRepository;
import com.saasno.utility.constant.ConfigurationKey;
import com.saasno.utility.picker.UserStatus;
import com.saasno.utility.cryptography.Totp;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.verification.StringVerification;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;

@Service
public class AuthenticationService {

	private static final int DEFAULT_MAX_LOGIN_ATTEMPTS = 5;
	private static final int DEFAULT_LOGIN_LOCK_MINUTES = 15;
	private static final String INVALID_CREDENTIALS = "Username atau kata sandi salah";

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ConfigurationRepository configurationRepository;

	@Autowired
	private SessionService sessionService;

	// dontRollbackOn: hitungan gagal harus tetap tersimpan walau ApiException dilempar.
	@Transactional(dontRollbackOn = ApiException.class)
	public ResponseModel<SessionEntity> authentication(HttpServletRequest request) {
		String username = request.getParameter("username");
		String password = request.getParameter("password");
		if (StringVerification.nullOrEmpty(username)) {
			throw new ApiException("400", "Username tidak boleh kosong");
		}

		if (StringVerification.nullOrEmpty(password)) {
			throw new ApiException("400", "Kata sandi tidak boleh kosong");
		}

		UserEntity found = userRepository.findByUsername(username);
		if (found == null) {
			// Pesan sama dengan kata sandi salah agar username yang valid tidak bisa ditebak.
			throw new ApiException("400", INVALID_CREDENTIALS);
		}

		// Baris dikunci agar tebakan paralel tidak lolos dari hitungan.
		UserEntity existingUser = userRepository.findLock(found.getId());
		if (existingUser.isLocked()) {
			throw lockedException(existingUser);
		}

		if (!existingUser.verifyPassword(password)) {
			throw failedAttempt(existingUser, INVALID_CREDENTIALS);
		}

		if (existingUser.getStatus() != UserStatus.ACTIVED) {
			throw new ApiException("403", "Akun tidak aktif, hubungi admin");
		}
		existingUser.clearFailedLogin();

		Integer expiredSeconds = 68400;
		String rememberMe = request.getParameter("rememberMe");
		if (!StringVerification.nullOrEmpty(rememberMe) && rememberMe.equalsIgnoreCase("true")) {
			expiredSeconds = 615600;
		}

		SessionEntity session = new SessionEntity(existingUser, expiredSeconds);
		if (Boolean.TRUE.equals(existingUser.getTwoFactorAuth())) {
			session.setTwoFactorPending(true);
		}
		sessionService.create(session);
		return new ResponseModel<SessionEntity>().success(session);
	}

	@Transactional(dontRollbackOn = ApiException.class)
	public ResponseModel<SessionEntity> verifyOtp(HttpServletRequest request, String code) {
		String authorization = request.getHeader("Authorization");
		if (StringVerification.nullOrEmpty(authorization)) {
			throw new ApiException("401", "Silakan login terlebih dahulu");
		}

		String[] authorizationValue = authorization.split(" ");
		if (authorizationValue.length != 2 || !authorizationValue[0].equals("Bearer")) {
			throw new ApiException("401", "Format otorisasi tidak valid");
		}

		SessionEntity session = sessionService.read(authorizationValue[1]);
		if (session == null) {
			throw new ApiException("401", "Sesi Anda telah berakhir, silakan login kembali");
		}

		if (!session.isTwoFactorPending()) {
			return new ResponseModel<SessionEntity>().success(session);
		}

		UserEntity found = userRepository.findByUsername(session.getUsername());
		UserEntity user = found == null ? null : userRepository.findLock(found.getId());
		if (user == null) {
			throw new ApiException("400", "Kode OTP tidak valid");
		}
		if (user.isLocked()) {
			sessionService.delete(authorizationValue[1]);
			throw lockedException(user);
		}
		if (StringVerification.nullOrEmpty(user.getTwoFactorAuthSecret())
				|| !Totp.verify(user.getTwoFactorAuthSecret(), code)) {
			ApiException failure = failedAttempt(user, "Kode OTP tidak valid");
			if (user.isLocked()) {
				// Sesi pending dibuang agar kode tidak bisa ditebak terus setelah akun terkunci.
				sessionService.delete(authorizationValue[1]);
			}
			throw failure;
		}
		user.clearFailedLogin();

		session.setTwoFactorPending(false);
		sessionService.create(session);
		return new ResponseModel<SessionEntity>().success(session);
	}

	@Transactional
	public ResponseModel<SessionEntity> unauthenticated(HttpServletRequest request) {
		String authorization = request.getHeader("Authorization");
		if (StringVerification.nullOrEmpty(authorization)) {
			return new ResponseModel<SessionEntity>().success();
		}

		String[] authorizationValue = authorization.split(" ");
		if (authorizationValue.length == 2) {
			sessionService.delete(authorizationValue[1]);
		}

		return new ResponseModel<SessionEntity>().success();
	}

	private ApiException failedAttempt(UserEntity user, String message) {
		int max = configInt(ConfigurationKey.MAX_LOGIN_ATTEMPTS, DEFAULT_MAX_LOGIN_ATTEMPTS);
		int minutes = configInt(ConfigurationKey.LOGIN_LOCK_MINUTES, DEFAULT_LOGIN_LOCK_MINUTES);
		boolean locked = user.recordFailedLogin(max, minutes);
		userRepository.update(user);
		if (locked) {
			return lockedException(user);
		}
		return new ApiException("400", message);
	}

	private ApiException lockedException(UserEntity user) {
		long minutes = Math.max(1, java.time.Duration.between(java.time.LocalDateTime.now(), user.getLockedUntil()).toMinutes() + 1);
		return new ApiException("429", "Terlalu banyak percobaan gagal. Coba lagi dalam " + minutes + " menit");
	}

	private int configInt(ConfigurationKey key, int fallback) {
		String value = configurationRepository.get(key);
		try {
			int parsed = value == null ? fallback : Integer.parseInt(value.trim());
			return parsed < 1 ? fallback : parsed;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

}
