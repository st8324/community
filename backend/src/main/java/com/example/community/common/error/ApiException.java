package com.example.community.common.error;

/** 비즈니스 규칙 위반을 표준 오류 응답으로 바꾸기 위한 예외. 스택트레이스를 만들지 않는다 — 정상 흐름의 일부(예: 중복 이메일)이고 로그에도 남기지 않는다. */
public class ApiException extends RuntimeException {

  private final ErrorCode code;

  public ApiException(ErrorCode code) {
    super(code.name(), null, false, false);
    this.code = code;
  }

  public ErrorCode code() {
    return code;
  }
}
