package com.example.community.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** AUTH-01 ⑤⑥ 이메일 정규화·형식 단위 테스트 (DB 없음). */
class EmailRulesTest {

  @Test
  void 앞뒤_공백을_지우고_소문자로_바꾼다() {
    assertThat(EmailRules.normalize("  Ab.C+Tag@Example.COM \t")).isEqualTo("ab.c+tag@example.com");
  }

  @Test
  void null은_null로_둔다() {
    assertThat(EmailRules.normalize(null)).isNull();
  }

  @Test
  void 소문자_변환은_시스템_로케일에_영향받지_않는다() {
    // 터키어 로케일에서 "I".toLowerCase()는 점 없는 ı가 된다. Locale.ROOT를 써야 한다.
    assertThat(EmailRules.normalize("ADMIN@EXAMPLE.COM")).isEqualTo("admin@example.com");
  }

  @ParameterizedTest
  @ValueSource(strings = {"a@example.com", "ab.c+tag_1%x-y@sub.example.co.kr", "user@example.io"})
  void 올바른_형식은_통과한다(String email) {
    assertThat(EmailRules.isValid(email)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "plain",
        "a@b",
        "a@example.c",
        "a b@example.com",
        "a@@example.com",
        "@example.com",
        "한글@example.com",
        "a@exämple.com",
        "a@example.com\n"
      })
  void 잘못된_형식은_거부한다(String email) {
    assertThat(EmailRules.isValid(email)).isFalse();
  }

  @Test
  void 최대_254자까지_허용한다() {
    String suffix = "@example.com"; // 12자
    assertThat(EmailRules.isValid("a".repeat(254 - suffix.length()) + suffix)).isTrue();
    assertThat(EmailRules.isValid("a".repeat(255 - suffix.length()) + suffix)).isFalse();
  }
}
