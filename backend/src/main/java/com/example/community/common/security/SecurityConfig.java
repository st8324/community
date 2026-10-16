package com.example.community.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 보안 필터 체인 (02 문서 4.3절, 5.4절). 보안 기능은 직접 만들지 않고 Spring Security 설정으로 쓴다 (02 문서 1장).
 *
 * <p>순서: CSRF 검증(상태 변경 요청) → 인증 → URL 단위 인가. 소유자 확인은 Service 계층(SEC-07).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityErrorResponses errors)
      throws Exception {
    http
        // SPA용 CSRF: XSRF-TOKEN 쿠키(JS 읽기 가능) → X-XSRF-TOKEN 헤더. 로그인·회원가입 POST에도 적용된다 (SEC-05).
        .csrf(csrf -> csrf.spa())
        // JWT 쿠키 방식 → 서버 세션을 만들지 않는다.
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable) // 로그아웃은 AuthController가 쿠키를 지운다 (AUTH-03)
        .requestCache(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.GET, "/api/csrf")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/signup")
                    .permitAll()
                    // 나머지 /api/**는 로그인 필요. 공개 조회 API는 해당 기능 PR에서 여기 명시적으로 연다.
                    .requestMatchers("/api/**")
                    .authenticated()
                    // 정적 파일(SPA), /actuator/health(노출 범위는 application.yml에서 health만)
                    .anyRequest()
                    .permitAll())
        .exceptionHandling(e -> e.authenticationEntryPoint(errors).accessDeniedHandler(errors));
    return http.build();
  }

  /** {@code {bcrypt}$2a$10$...} 형식. 접두사 덕분에 나중에 알고리즘을 바꿔도 기존 해시를 검증할 수 있다 (SEC-02). */
  @Bean
  PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }
}
