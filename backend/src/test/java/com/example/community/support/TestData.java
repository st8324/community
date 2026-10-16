package com.example.community.support;

import java.util.UUID;

/**
 * 테스트 입력값 생성기.
 *
 * <p>테스트는 DB를 비우지 않는다. 대신 매번 고유한 이메일을 써서 테스트끼리 간섭하지 않게 한다 (03 문서 5.1절). 비밀번호도 실행 시 만들어 저장소에 비밀번호처럼
 * 보이는 문자열을 두지 않는다 (Gitleaks 예외 불필요).
 */
public final class TestData {

  private TestData() {}

  /** 예: {@code u3f2a9c1d@example.com} — AUTH-01 ⑥ 형식을 만족하는 소문자 이메일. */
  public static String uniqueEmail() {
    return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "@example.com";
  }

  /** SEC-03을 만족하는 무작위 비밀번호 (ASCII 39자). */
  public static String validPassword() {
    return "pw-" + UUID.randomUUID();
  }
}
