package com.saasno.service;

import java.io.IOException;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import com.saasno.entity.ApiEntity;
import com.saasno.entity.RoleEntity;
import com.saasno.entity.SessionEntity;
import com.saasno.repository.ApiRepository;
import com.saasno.repository.RoleRepository;
import com.saasno.utility.constant.Path;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.verification.StringVerification;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class SessionService {

	@Autowired
	private RedisTemplate<String, Object> redisTemplate;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private ApiRepository apiRepository;

	private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

	static {
		PATH_MATCHER.setCaseSensitive(false);
	}

	private final String PREFIX = "session:";

	public void create(SessionEntity session) {
		long ttl = (session.getExpired().getTime() - System.currentTimeMillis()) / 1000;
		if (ttl > 0) {
			redisTemplate.opsForValue().set(
					PREFIX + session.getId(),
					session,
					ttl,
					TimeUnit.SECONDS);
		}
	}

	public SessionEntity read(String sessionId) {
		return (SessionEntity) redisTemplate.opsForValue().get(PREFIX + sessionId);
	}

	public void delete(String sessionId) {
		redisTemplate.delete(PREFIX + sessionId);
	}

	public void deleteAllByUserId(String username) {
		String pattern = PREFIX + ":" + username + ":*";
		Set<String> keys = redisTemplate.keys(pattern);
		if (keys != null && !keys.isEmpty()) {
			redisTemplate.delete(keys);
		}
	}

	public ResponseModel<SessionEntity> check(HttpServletRequest request, String path, String method)
			throws IOException {
		ResponseModel<SessionEntity> session = check(request);

		if (session.getData().isTwoFactorPending()) {
			boolean isOtpVerifyEndpoint = path.equals(Path.API_LOGIN_OTP_VERIFY) && method.equalsIgnoreCase("POST");
			if (isOtpVerifyEndpoint) {
				return session;
			}
			throw new ApiException("401", "Verifikasi OTP diperlukan");
		}

		if (session.getData().getRoles() == null || session.getData().getRoles().isEmpty()) {
			throw new ApiException("404", "Role user tidak dikenal");
		}

		ApiEntity target = findBestMatchingApi(path, method);
		if (target != null) {
			for (String roleId : session.getData().getRoles()) {
				RoleEntity role = roleRepository.find(roleId);

				if (role != null && role.getApis() != null) {
					boolean hasMatch = role.getApis().stream().anyMatch(api -> api.getId().equals(target.getId()));

					if (hasMatch) {
						return session;
					}
				}
			}
		}

		throw new ApiException("403", "Akun Anda tidak memiliki akses ke fitur ini");
	}

	/**
	 * Find the most specific registered API matching the request, so a pattern like
	 * /api/{id}/product never grants access to a more specific static path such as
	 * /api/warehouse/product.
	 */
	private ApiEntity findBestMatchingApi(String path, String method) {
		Comparator<String> comparator = PATH_MATCHER.getPatternComparator(path);
		return apiRepository.findByMethod(method).stream()
				.filter(api -> PATH_MATCHER.match(api.getPath(), path))
				.min((a, b) -> comparator.compare(a.getPath(), b.getPath()))
				.orElse(null);
	}

	public ResponseModel<SessionEntity> check(HttpServletRequest request) {
		String authorization = request.getHeader("Authorization");
		if (StringVerification.nullOrEmpty(authorization)) {
			throw new ApiException("401", "Silakan login terlebih dahulu");
		}

		String[] authorizationValue = authorization.split(" ");
		if (authorizationValue.length != 2) {
			throw new ApiException("401", "Format otorisasi tidak valid");
		}

		if (!authorizationValue[0].equals("Bearer")) {
			throw new ApiException("401", "Jenis otorisasi tidak valid");
		}

		SessionEntity session = read(authorizationValue[1]);
		if (session == null) {
			throw new ApiException("401", "Sesi Anda telah berakhir, silakan login kembali");
		}

		return new ResponseModel<SessionEntity>().success(session);
	}
}
