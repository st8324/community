package com.example.community.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * SEC-03 비밀번호 정책 단위 테스트 (DB 없음).
 *
 * <p>핵심 함정 두 가지를 경계값으로 고정한다. ① 자바 {@code String.length()}는 UTF-16 단위라 이모지 1개를 2자로 센다 → "8~64자"는
 * 유니코드 문자(code point) 수로 센다. ② BCrypt는 72바이트 이후를 무시한다 → 문자 수와 별개로 UTF-8 72바이트 상한을 둔다.
 */
class PasswordRulesTest {

  static final String SMILE = "😀"; // 😀 (UTF-16 2단위, UTF-8 4바이트)

  @Test
  void null은_거부한다() {
    assertThat(PasswordRules.isValidForSignup(null)).isFalse();
    assertThat(PasswordRules.isWithinUpperBounds(null)).isFalse();
  }

  @Test
  void 영문_7자는_거부하고_8자는_허용한다() {
    assertThat(PasswordRules.isValidForSignup("a".repeat(7))).isFalse();
    assertThat(PasswordRules.isValidForSignup("a".repeat(8))).isTrue();
  }

  @Test
  void 영문_64자는_허용하고_65자는_거부한다() {
    assertThat(PasswordRules.isValidForSignup("a".repeat(64))).isTrue();
    assertThat(PasswordRules.isValidForSignup("a".repeat(65))).isFalse();
  }

  @Test
  void 이모지_7개는_UTF16_길이로는_14지만_7자이므로_거부한다() {
    String sevenSmiles = SMILE.repeat(7);
    assertThat(sevenSmiles.length()).isEqualTo(14); // 함정 확인
    assertThat(PasswordRules.isValidForSignup(sevenSmiles)).isFalse();
    assertThat(PasswordRules.isValidForSignup(SMILE.repeat(8))).isTrue();
  }

  @Test
  void 한글_24자_72바이트는_허용하고_25자_75바이트는_거부한다() {
    assertThat(PasswordRules.isValidForSignup("가".repeat(24))).isTrue();
    assertThat(PasswordRules.isValidForSignup("가".repeat(25))).isFalse();
  }

  @Test
  void 이모지_18개_72바이트는_허용하고_19개_76바이트는_거부한다() {
    assertThat(PasswordRules.isValidForSignup(SMILE.repeat(18))).isTrue();
    assertThat(PasswordRules.isValidForSignup(SMILE.repeat(19))).isFalse();
  }

  @Test
  void 앞뒤_공백도_비밀번호의_일부로_센다() {
    assertThat(PasswordRules.isValidForSignup(" abcdef ")).isTrue(); // 공백 포함 8자
  }

  @Test
  void 로그인용_상한_검사는_최소_길이를_보지_않지만_빈_값과_상한_초과는_거부한다() {
    assertThat(PasswordRules.isWithinUpperBounds("a")).isTrue();
    assertThat(PasswordRules.isWithinUpperBounds("")).isFalse();
    assertThat(PasswordRules.isWithinUpperBounds("a".repeat(65))).isFalse();
    assertThat(PasswordRules.isWithinUpperBounds("가".repeat(25))).isFalse();
  }
}
