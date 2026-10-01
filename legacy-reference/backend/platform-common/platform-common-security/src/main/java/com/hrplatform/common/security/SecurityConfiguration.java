package com.hrplatform.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrplatform.common.api.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    public JwtService jwtService(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.issuer:hr-platform}") String issuer,
            @Value("${security.jwt.access-token-ttl:30m}") Duration ttl) {
        return new JwtService(secret, issuer, ttl);
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService) {
        return new JwtAuthenticationFilter(jwtService);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   ObjectMapper objectMapper) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/api/auth/login").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new JsonAuthenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(new JsonAccessDeniedHandler(objectMapper)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void write(HttpServletResponse response,
                              ObjectMapper objectMapper,
                              int status,
                              String code,
                              String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.failure(code, message, com.hrplatform.common.api.TraceId.current())));
    }

    private record JsonAuthenticationEntryPoint(ObjectMapper objectMapper) implements AuthenticationEntryPoint {
        @Override
        public void commence(HttpServletRequest request,
                             HttpServletResponse response,
                             AuthenticationException authException) throws IOException {
            write(response, objectMapper, 401, "AUTH_REQUIRED", "Authentication required");
        }
    }

    private record JsonAccessDeniedHandler(ObjectMapper objectMapper) implements AccessDeniedHandler {
        @Override
        public void handle(HttpServletRequest request,
                           HttpServletResponse response,
                           org.springframework.security.access.AccessDeniedException accessDeniedException)
                throws IOException {
            write(response, objectMapper, 403, "FORBIDDEN", "Access denied");
        }
    }
}
