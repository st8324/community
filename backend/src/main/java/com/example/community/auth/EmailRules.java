package com.example.community.auth;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * AUTH-01 ⑤⑥ 이메일 정규화·형식 규칙 (01 문서 v0.3, D-11).
 *
 * <p>ASCII만 허용한다: 유니코드 유사 문자(동형 문자)로 다른 사람처럼 보이는 표시명을 막고, 소문자 변환 결과가 로케일·유니코드 버전에 따라 달라지는 문제를 피한다.
 */
public final class EmailRules {

  public static final int MAX_LENGTH = 254;

  /** 로컬부 {@code 영문·숫자·._%+-} + {@code @} + 도메인 {@code 영문·숫자·.-} + {@code .} + 영문 2자 이상. */
  private static final Pattern FORMAT =
      Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

  private EmailRules() {}

  /** 앞뒤 공백 제거 → 소문자. {@link Locale#ROOT}로 고정해 시스템 로케일(예: 터키어 i/ı)에 영향받지 않는다. */
  public static String normalize(String raw) {
    return raw == null ? null : raw.strip().toLowerCase(Locale.ROOT);
  }

  /** 정규화된 값을 검사한다. 길이를 먼저 확인해 긴 입력에 정규식을 돌리지 않는다. */
  public static boolean isValid(String normalized) {
    return normalized != null
        && normalized.length() <= MAX_LENGTH
        && FORMAT.matcher(normalized).matches();
  }
}
