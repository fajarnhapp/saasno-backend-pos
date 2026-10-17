package com.saasno.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;

import com.saasno.filter.SessionFilter;
import com.saasno.service.SessionService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.cryptography.Argon2;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

	@Bean
	public SessionFilter sessionFilter(SessionService sessionService) {
		return new SessionFilter(sessionService);
	}

	@Bean
	public SecurityFilterChain SecurityFilterChain(HttpSecurity http, SessionFilter sessionFilter) throws Exception {
		return http
				.csrf(csrf -> csrf.disable())
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(Path.API_LOGIN + "/**").permitAll()
						.requestMatchers(Path.API_PUBLIC + "/**").permitAll()
						.anyRequest().authenticated())
				.addFilterBefore(sessionFilter, AbstractPreAuthenticatedProcessingFilter.class)
				.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return Argon2.setDefault;
	}
}
