package com.example.community.common.error;

import java.util.List;

/** 표준 오류 응답 {@code {code, message, fields}} (01 문서 v0.3 4.5절). fields에는 필드 이름만, 값은 넣지 않는다. */
public record ErrorResponse(String code, String message, List<String> fields) {

  public static ErrorResponse of(ErrorCode code) {
    return new ErrorResponse(code.name(), code.message(), List.of());
  }

  public static ErrorResponse of(ErrorCode code, List<String> fields) {
    return new ErrorResponse(code.name(), code.message(), List.copyOf(fields));
  }
}
