package com.saasno.filter;

import java.io.IOException;
import java.util.stream.Collectors;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.saasno.entity.SessionEntity;
import com.saasno.service.SessionService;
import com.saasno.utility.ConverterUtility;
import com.saasno.utility.constant.Path;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.logger.Log;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.verification.StringVerification;
import com.saasno.utility.wrapper.MultipleReadRequestWrapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class SessionFilter extends OncePerRequestFilter {

	private final SessionService sessionService;

	public SessionFilter(SessionService sessionService) {
		this.sessionService = sessionService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		response.setContentType(MediaType.APPLICATION_JSON_VALUE);

		try {
			String path = request.getRequestURI();
			if (StringVerification.nullOrEmpty(path)) {
				throw new ApiException("404", "Tidak ditemukan");
			}

			String contentType = request.getContentType();
			boolean isMultipart = contentType != null && contentType.toLowerCase().contains("multipart/form-data");
			boolean isGet = request.getMethod().equalsIgnoreCase(HttpMethod.GET.toString());
			boolean isLoginEndpoint = path.equals(Path.API_LOGIN);

			HttpServletRequest req = request;
			if (!isGet && !isLoginEndpoint && !isMultipart) {
				req = new MultipleReadRequestWrapper(request);
			}
			if (!path.startsWith(Path.API_PUBLIC) && !isLoginEndpoint) {
				ResponseModel<SessionEntity> session = sessionService.check(request, path, request.getMethod());
				SecurityContextHolder.getContext().setAuthentication(
						new UsernamePasswordAuthenticationToken(
								session.getData(),
								null,
								session.getData().getRoles()
										.stream()
										.map(SimpleGrantedAuthority::new)
										.collect(Collectors.toSet())));
			}
			filterChain.doFilter(req, response);
		} catch (ApiException e) {
			SecurityContextHolder.clearContext();
			response.setStatus(Integer.parseInt(e.getCode()));
			response.getWriter().println(ConverterUtility.toPrettyString(new ResponseModel<>(e.getMessage())));
		} catch (Exception e) {
			SecurityContextHolder.clearContext();
			Log.error(e.getMessage(), e);
			response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
			response.getWriter()
					.println(ConverterUtility.toPrettyString(new ResponseModel<String>().internalServerError()));
		}
	}
}
