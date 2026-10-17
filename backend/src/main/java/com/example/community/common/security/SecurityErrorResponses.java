package com.example.community.common.security;

import com.example.community.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

/**
 * 보안 필터 단계(컨트롤러 도달 전) 오류를 표준 형식으로 쓴다 — 401 {@code UNAUTHORIZED}, 403 {@code FORBIDDEN}/{@code
 * CSRF_INVALID}.
 *
 * <p>기본 구현은 {@code WWW-Authenticate} 헤더에 "Jwt expired at ..." 같은 상세를 담는다. 여기서는 예외 내용을 쓰지 않는다
 * (SEC-10). 본문은 {@link ErrorCode}의 고정 문장으로만 만들기 때문에 JSON 라이브러리 없이 안전하게 조립할 수 있다.
 */
@Component
public class SecurityErrorResponses implements AuthenticationEntryPoint, AccessDeniedHandler {

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    write(response, ErrorCode.UNAUTHORIZED);
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    write(
        response,
        accessDeniedException instanceof CsrfException
            ? ErrorCode.CSRF_INVALID
            : ErrorCode.FORBIDDEN);
  }

  private static void write(HttpServletResponse response, ErrorCode code) throws IOException {
    response.setStatus(code.status().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response
        .getWriter()
        .write(
            "{\"code\":\"%s\",\"message\":\"%s\",\"fields\":[]}"
                .formatted(code.name(), code.message()));
  }
}
