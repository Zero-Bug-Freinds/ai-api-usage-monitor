package com.zerobugfreinds.team_service.config;

import com.zerobugfreinds.team_service.security.GatewayHeaderContextFilter;
import com.zerobugfreinds.team_service.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
	private final GatewayHeaderContextFilter gatewayHeaderContextFilter;
	private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;

	public SecurityConfig(
			GatewayHeaderContextFilter gatewayHeaderContextFilter,
			RestAuthenticationEntryPoint restAuthenticationEntryPoint
	) {
		this.gatewayHeaderContextFilter = gatewayHeaderContextFilter;
		this.restAuthenticationEntryPoint = restAuthenticationEntryPoint;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(exception -> exception.authenticationEntryPoint(restAuthenticationEntryPoint))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/internal/teams/**").permitAll()
						.requestMatchers("/internal/v1/team-invitations/**").permitAll()
						.requestMatchers("/internal/admin/**").permitAll()
						.requestMatchers("/error").permitAll()
						.anyRequest().authenticated()
				)
				.addFilterBefore(gatewayHeaderContextFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}
}
