package com.hrplatform.common.security;

import com.hrplatform.auth.SysUserMapper;
import com.hrplatform.common.web.TraceIdFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Duration;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
    JwtService jwtService(
            @Value("${platform.jwt.secret}") String secret,
            @Value("${platform.jwt.expiration:PT8H}") Duration expiration
    ) {
        return new JwtService(secret, expiration);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService, SysUserMapper userMapper,
                                            TokenBlacklistService tokenBlacklist,
                                            @Value("${platform.security.live-jwt-validation:true}") boolean liveValidation)
            throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/refresh").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new TraceIdFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, userMapper, liveValidation, tokenBlacklist),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
