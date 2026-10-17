package com.example.community.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** A-1 표시명 마스킹 규칙 (01 문서 v0.3 8장). */
class EmailMaskerTest {

  @ParameterizedTest(name = "{0} → {1}")
  @CsvSource({
    "abcdef@example.com, ab***@example.com",
    "abc@example.com,    ab***@example.com",
    "ab@example.com,     a***@example.com",
    "a@example.com,      ***@example.com",
    "x.y+tag@sub.example.co.kr, x.***@sub.example.co.kr"
  })
  void 로컬부_길이에_따라_앞부분만_남기고_도메인은_그대로_둔다(String email, String expected) {
    assertThat(EmailMasker.mask(email)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({"abcdef@example.com", "ab@example.com", "a@example.com"})
  void 마스킹_결과에는_로컬부_전체가_나타나지_않는다(String email) {
    String local = email.substring(0, email.indexOf('@'));
    assertThat(EmailMasker.mask(email)).doesNotContain(local + "@");
  }
}
