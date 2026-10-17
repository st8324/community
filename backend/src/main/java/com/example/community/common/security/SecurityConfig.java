package com.example.community.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

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
        // CSRF는 DSL(.csrf) 대신 Spring의 CsrfFilter를 직접 등록한다 (03 문서 T-14, HD-25).
        // 이유: oauth2ResourceServer DSL은 "bearer 토큰이 있는 요청"을 CSRF 검사에서 자동 제외한다. 헤더 토큰이면 안전하지만,
        // 이 앱의 토큰은 브라우저가 자동으로 보내는 쿠키라서 그 제외가 곧 CSRF 방어 해제가 된다 (CI의 S-06이 실제로 잡음).
        .csrf(AbstractHttpConfigurer::disable)
        .addFilterAt(csrfFilter(errors), CsrfFilter.class)
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
                    // 로그인 없이 호출하는 인증 API. 연장은 리프레시 쿠키로, 로그아웃은 항상 204 (01 v0.4 D-17)
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/auth/signup",
                        "/api/auth/login",
                        "/api/auth/refresh",
                        "/api/auth/logout")
                    .permitAll()
                    // 나머지 /api/**는 로그인 필요. 공개 조회 API는 해당 기능 PR에서 여기 명시적으로 연다.
                    .requestMatchers("/api/**")
                    .authenticated()
                    // 정적 파일(SPA), /actuator/health(노출 범위는 application.yml에서 health만)
                    .anyRequest()
                    .permitAll())
        // JWT 검증은 Resource Server에 맡긴다 (서명·만료·알고리즘은 JwtConfig의 디코더 설정). 토큰은 쿠키에서만 읽는다.
        .oauth2ResourceServer(
            rs ->
                rs.bearerTokenResolver(new CookieBearerTokenResolver())
                    .authenticationEntryPoint(errors)
                    .accessDeniedHandler(errors)
                    .jwt(Customizer.withDefaults()))
        .exceptionHandling(e -> e.authenticationEntryPoint(errors).accessDeniedHandler(errors));
    return http.build();
  }

  /**
   * SPA용 CSRF (SEC-05): {@code XSRF-TOKEN} 쿠키(JS가 읽음) → {@code X-XSRF-TOKEN} 헤더로 되돌려 보낸다. 로그인·회원가입을
   * 포함한 모든 상태 변경 요청에 적용되고, 토큰 쿠키 유무와 관계없이 예외가 없다. 토큰은 처음 필요할 때 만들어 쿠키로 저장한다({@code GET /api/csrf}).
   */
  private static CsrfFilter csrfFilter(SecurityErrorResponses errors) {
    CsrfFilter filter = new CsrfFilter(CookieCsrfTokenRepository.withHttpOnlyFalse());
    filter.setRequestHandler(new CsrfTokenRequestAttributeHandler());
    filter.setAccessDeniedHandler(errors);
    return filter;
  }

  /** {@code {bcrypt}$2a$10$...} 형식. 접두사 덕분에 나중에 알고리즘을 바꿔도 기존 해시를 검증할 수 있다 (SEC-02). */
  @Bean
  PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }
}
