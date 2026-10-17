package com.example.community.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.community.auth.Role;
import com.example.community.support.AuthFixtures;
import com.example.community.support.AuthFixtures.Account;
import com.example.community.support.IntegrationTest;
import com.example.community.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * S-16 (AUTH-02 ②): 응답만 보고 "이 이메일이 가입돼 있는지" 알 수 없어야 한다.
 *
 * <p>응답 시간 차이도 단서가 될 수 있어, 구현은 없는 이메일에도 가짜 해시로 BCrypt 비교를 한 번 수행한다. 시간은 환경에 따라 흔들려 자동 테스트로 고정하지 않는다
 * (코드 리뷰 항목).
 */
@IntegrationTest
class LoginEnumerationSecurityTest {

  @Autowired AuthFixtures fixtures;

  @Test
  void unknownEmailAndWrongPasswordLookTheSame() throws Exception {
    Account existing = fixtures.createUser(Role.USER);

    MockHttpServletResponse wrongPassword =
        fixtures.loginResult(existing.email(), TestData.validPassword()).getResponse();
    MockHttpServletResponse unknownEmail =
        fixtures.loginResult(TestData.uniqueEmail(), TestData.validPassword()).getResponse();

    assertThat(wrongPassword.getStatus()).isEqualTo(401).isEqualTo(unknownEmail.getStatus());
    assertThat(wrongPassword.getContentType()).isEqualTo(unknownEmail.getContentType());
    assertThat(wrongPassword.getContentAsByteArray())
        .isEqualTo(unknownEmail.getContentAsByteArray());
    assertThat(wrongPassword.getHeaders("Set-Cookie"))
        .noneMatch(l -> l.startsWith(AuthFixtures.ACCESS_TOKEN + "="));
    assertThat(unknownEmail.getHeaders("Set-Cookie"))
        .noneMatch(l -> l.startsWith(AuthFixtures.ACCESS_TOKEN + "="));
  }
}
