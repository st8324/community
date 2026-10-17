package com.example.community.common.error;

import org.springframework.http.HttpStatus;

/**
 * 오류 코드 목록 (01 문서 v0.3 4.5절). 메시지는 고정 문장만 쓴다 — 입력값·예외 메시지를 끼워 넣지 않는다 (SEC-10).
 *
 * <p>메시지에 큰따옴표·역슬래시를 쓰지 않는다: {@code SecurityErrorResponses}가 필터 단계에서 JSON을 직접 만든다.
 */
public enum ErrorCode {
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
  INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다."),
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
  FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
  CSRF_INVALID(HttpStatus.FORBIDDEN, "보안 토큰이 없거나 만료되었습니다. 새로고침 후 다시 시도해 주세요."),
  NOT_FOUND(HttpStatus.NOT_FOUND, "찾을 수 없습니다."),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
  EMAIL_ALREADY_USED(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
  UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "JSON 형식으로 보내 주세요."),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다.");

  private final HttpStatus status;
  private final String message;

  ErrorCode(HttpStatus status, String message) {
    this.status = status;
    this.message = message;
  }

  public HttpStatus status() {
    return status;
  }

  public String message() {
    return message;
  }
}
