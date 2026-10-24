package com.example.community.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.community.auth.Role;
import com.example.community.auth.User;
import com.example.community.auth.UserRepository;
import com.example.community.support.AuthFixtures;
import com.example.community.support.IntegrationTest;
import com.example.community.support.TestData;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * ADM-01 ①⑦ 관리자 시드: 환경변수(속성)가 있으면 기동 시 ADMIN 계정이 만들어지고 일반 로그인이 된다 (01 문서 v0.6).
 *
 * <p>시드는 앱 기동 때 한 번 실행되므로 요청 단위 테스트로 볼 수 없다 — 시드 속성을 주입한 <b>별도 컨텍스트</b>에서 실행한다 (03 문서 HD-35).
 * 이메일·비밀번호는 실행할 때마다 무작위로 만든다 (CLAUDE.md 절대 규칙 5).
 */
@IntegrationTest
class AdminSeedStartupTest {

  static final String EMAIL =
      "seed-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
  static final String PASSWORD = TestData.validPassword();

  @DynamicPropertySource
  static void seedProperties(DynamicPropertyRegistry registry) {
    registry.add("community.admin.email", () -> EMAIL);
    registry.add("community.admin.password", () -> PASSWORD);
  }

  @Autowired UserRepository users;
  @Autowired AuthFixtures fixtures;
  @Autowired AdminSeedRunner runner;
  @Autowired JdbcTemplate jdbc;

  @Test
  void 기동하면_ADMIN_계정이_만들어지고_비밀번호는_BCrypt로_저장된다() {
    User admin = users.findByEmail(EMAIL).orElseThrow();

    assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
    assertThat(admin.getPasswordHash()).startsWith("$2").isNotEqualTo(PASSWORD);
  }

  @Test
  void 만들어진_관리자는_일반_로그인으로_로그인한다() throws Exception {
    MvcResult result = fixtures.loginResult(EMAIL, PASSWORD);

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(AuthFixtures.accessTokenSetCookie(result)).isPresent();
  }

  @Test
  void 시드를_다시_실행해도_계정은_하나다() throws Exception {
    runner.run(null);
    runner.run(null);

    Integer count =
        jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, EMAIL);
    assertThat(count).isEqualTo(1);
    assertThat(users.findByEmail(EMAIL).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
  }
}
