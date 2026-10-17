package com.example.community.common.error;

import java.util.List;

/**
 * 비즈니스 규칙 위반을 표준 오류 응답으로 바꾸기 위한 예외. 스택트레이스를 만들지 않는다 — 정상 흐름의 일부(예: 중복 이메일)이고 로그에도 남기지 않는다.
 *
 * <p>{@code fields}에는 필드 <b>이름</b>만 넣는다 (값 금지, 01 문서 4.5절).
 */
public class ApiException extends RuntimeException {

  private final ErrorCode code;
  private final List<String> fields;

  public ApiException(ErrorCode code) {
    this(code, List.of());
  }

  public ApiException(ErrorCode code, List<String> fields) {
    super(code.name(), null, false, false);
    this.code = code;
    this.fields = List.copyOf(fields);
  }

  public ErrorCode code() {
    return code;
  }

  public List<String> fields() {
    return fields;
  }
}
