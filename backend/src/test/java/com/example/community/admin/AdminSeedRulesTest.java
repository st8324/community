package com.example.community.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.community.admin.AdminSeeder.Outcome;
import com.example.community.auth.Role;
import com.example.community.auth.User;
import com.example.community.auth.UserRepository;
import com.example.community.support.AuthFixtures;
import com.example.community.support.AuthFixtures.Account;
import com.example.community.support.IntegrationTest;
import com.example.community.support.TestData;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * ADM-01 ②~⑥ 관리자 시드 규칙 (01 문서 v0.6, D-32). 기본 컨텍스트에는 시드 속성이 없다 — 속성이 있는 경우는 {@link
 * AdminSeedStartupTest}가 별도 컨텍스트에서 본다 (03 문서 HD-35). 규칙은 {@link AdminSeeder}에 값을 직접 넘겨 확인한다.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class AdminSeedRulesTest {

  @Autowired AdminSeeder seeder;
  @Autowired AdminSeedRunner runner;
  @Autowired UserRepository users;
  @Autowired AuthFixtures fixtures;

  static String uniqueEmail() {
    return "rule-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
  }

  // ── ③ 시크릿이 없으면 건너뛰고 경고 ─────────────────────────

  @Test
  void 시크릿이_없으면_건너뛴다() {
    assertThat(seeder.seed(null, null)).isEqualTo(Outcome.SKIPPED_NO_SECRET);
    assertThat(seeder.seed("", "   ")).isEqualTo(Outcome.SKIPPED_NO_SECRET);
    assertThat(seeder.seed(uniqueEmail(), "")).isEqualTo(Outcome.SKIPPED_NO_SECRET);
  }

  @Test
  void 기본_컨텍스트에는_시크릿이_없어_기동_시드가_건너뛰어지고_경고만_남는다(CapturedOutput output) throws Exception {
    assertThatCode(() -> runner.run(null)).doesNotThrowAnyException();

    assertThat(output.getAll()).contains("관리자 시드").contains("건너뜀");
  }

  // ── ② 같은 이메일의 계정이 있으면 아무것도 바꾸지 않는다 (D-32) ──

  @Test
  void 같은_이메일의_일반_회원이_있으면_승격도_비밀번호_변경도_없다() throws Exception {
    Account member = fixtures.createUser(Role.USER);
    String attackerPassword = TestData.validPassword();

    Outcome outcome = seeder.seed(member.email(), attackerPassword);

    assertThat(outcome).isEqualTo(Outcome.SKIPPED_EXISTS);
    User after = users.findByEmail(member.email()).orElseThrow();
    assertThat(after.getRole()).isEqualTo(Role.USER);
    assertThat(fixtures.loginResult(member.email(), member.password()).getResponse().getStatus())
        .as("원래 비밀번호로 로그인된다")
        .isEqualTo(200);
    assertThat(fixtures.loginResult(member.email(), attackerPassword).getResponse().getStatus())
        .as("시드 비밀번호로는 로그인되지 않는다")
        .isEqualTo(401);
  }

  @Test
  void 대소문자가_달라도_같은_이메일로_본다() {
    Account member = fixtures.createUser(Role.USER);

    Outcome outcome = seeder.seed(member.email().toUpperCase(), TestData.validPassword());

    assertThat(outcome).isEqualTo(Outcome.SKIPPED_EXISTS);
    assertThat(users.findByEmail(member.email()).orElseThrow().getRole()).isEqualTo(Role.USER);
  }

  // ── ④ 가입과 같은 검증 ─────────────────────────────────────

  @Test
  void 이메일은_가입과_같이_공백_제거_소문자로_정규화해_저장한다() {
    String email = uniqueEmail();

    Outcome outcome = seeder.seed("  " + email.toUpperCase() + " ", TestData.validPassword());

    assertThat(outcome).isEqualTo(Outcome.CREATED);
    assertThat(users.findByEmail(email).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
  }

  @Test
  void 잘못된_이메일이면_계정을_만들지_않는다() {
    for (String bad :
        new String[] {"not-an-email", "a@b", "한글@example.com", "x".repeat(250) + "@a.io"}) {
      assertThat(seeder.seed(bad, TestData.validPassword()))
          .as(bad)
          .isEqualTo(Outcome.SKIPPED_INVALID_EMAIL);
    }
    assertThat(users.findByEmail("not-an-email")).isEmpty();
  }

  @Test
  void 약한_비밀번호면_계정을_만들지_않는다() {
    String shortPassword = "abc1234"; // 7자
    String tooLong = "a".repeat(65);
    String tooManyBytes = "가".repeat(25); // 25자 · 75바이트
    for (String bad : new String[] {shortPassword, tooLong, tooManyBytes}) {
      String email = uniqueEmail();
      assertThat(seeder.seed(email, bad)).isEqualTo(Outcome.SKIPPED_INVALID_PASSWORD);
      assertThat(users.findByEmail(email)).as("계정이 생기면 안 된다").isEmpty();
    }
  }

  @Test
  void 비밀번호의_앞뒤_공백은_제거하지_않는다() throws Exception {
    String email = uniqueEmail();
    String password = "  " + TestData.validPassword() + "  ";

    assertThat(seeder.seed(email, password)).isEqualTo(Outcome.CREATED);

    assertThat(fixtures.loginResult(email, password).getResponse().getStatus()).isEqualTo(200);
  }

  // ── ⑤ ⑥ 로그에 값이 없고, 실패해도 기동을 막지 않는다 ─────────

  @Test
  void 어떤_경우에도_로그에_비밀번호와_이메일_원문이_남지_않는다(CapturedOutput output) {
    String email = uniqueEmail();
    String password = TestData.validPassword();
    Account member = fixtures.createUser(Role.USER);

    seeder.seed(email, password); // 생성
    seeder.seed(email, password); // 이미 있음
    seeder.seed(member.email(), password); // 일반 회원과 충돌
    seeder.seed("bad-" + email.replace("@", "_"), password); // 이메일 형식 오류
    seeder.seed(uniqueEmail(), "short" + password.substring(0, 1)); // 약한 비밀번호

    assertThat(output.getAll())
        .doesNotContain(password)
        .doesNotContain(email)
        .doesNotContain(member.email());
  }

  @Test
  void 시드가_예외를_던져도_run은_정상_종료하고_로그에는_원인_종류만_남는다(CapturedOutput output) {
    String secret = TestData.validPassword();
    AdminSeeder failing =
        new AdminSeeder(null, null, null) {
          @Override
          public Outcome seed(String email, String password) {
            throw new IllegalStateException("DB 연결 실패: " + email + " " + password);
          }
        };
    AdminSeedRunner failingRunner = new AdminSeedRunner(failing, "boom@example.com", secret);

    assertThatCode(() -> failingRunner.run(null)).doesNotThrowAnyException();

    assertThat(output.getAll())
        .contains("IllegalStateException")
        .doesNotContain(secret)
        .doesNotContain("boom@example.com");
  }
}
