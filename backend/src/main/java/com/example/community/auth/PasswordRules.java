package com.example.community.auth;

import java.nio.charset.StandardCharsets;

/**
 * SEC-03 비밀번호 정책 (01 문서 v0.3 A-2, D-12).
 *
 * <ul>
 *   <li>길이는 유니코드 문자(code point) 수로 센다. {@code String.length()}는 이모지 1개를 2로 센다.
 *   <li>UTF-8 72바이트 상한: BCrypt는 72바이트 이후를 무시한다.
 *   <li>앞뒤 공백을 제거하지 않는다 (공백도 비밀번호의 일부).
 * </ul>
 */
public final class PasswordRules {

  public static final int MIN_CHARS = 8;
  public static final int MAX_CHARS = 64;
  public static final int MAX_BYTES = 72;

  private PasswordRules() {}

  /** 회원가입: 최소·최대 길이와 바이트 상한 모두. */
  public static boolean isValidForSignup(String raw) {
    return raw != null && charCount(raw) >= MIN_CHARS && isWithinUpperBounds(raw);
  }

  /**
   * 로그인: 비어 있지 않고 상한 이내인지만 본다 (AUTH-02 ⑤). 상한을 넘는 값으로는 가입할 수 없으므로 계정 존재 여부와 무관하게 400을 줄 수 있고,
   * BCrypt에 큰 입력이 들어가는 것도 막는다.
   */
  public static boolean isWithinUpperBounds(String raw) {
    if (raw == null || raw.isEmpty()) {
      return false;
    }
    // 바이트 수 ≥ 문자 수이므로, 문자 수 상한을 먼저 확인해 큰 입력의 인코딩 비용을 줄인다.
    if (raw.length() > MAX_CHARS * 2 || charCount(raw) > MAX_CHARS) {
      return false;
    }
    return raw.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
  }

  private static int charCount(String raw) {
    return raw.codePointCount(0, raw.length());
  }
}
