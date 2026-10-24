package com.example.community.admin;

import com.example.community.auth.EmailRules;
import com.example.community.auth.PasswordRules;
import com.example.community.auth.Role;
import com.example.community.auth.User;
import com.example.community.auth.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 관리자 계정 시드 규칙 (ADM-01, 01 D-32). 검증은 가입과 <b>같은 규칙 클래스</b>({@link EmailRules}·{@link
 * PasswordRules})와 같은 해시기를 쓴다 — 규칙이 둘로 갈라져 시드 계정만 약해지는 일이 없게 한다 (02 T-24). 보안 기능을 새로 만들지 않는다.
 *
 * <p>이미 같은 이메일의 계정이 있으면 역할·비밀번호를 포함해 <b>아무것도 바꾸지 않는다</b> — 일반 회원이 먼저 그 이메일로 가입해 둔 경우 시드가 그 계정을 관리자로
 * 승격하는 경로를 만들지 않기 위해서다. 이 클래스는 이메일·비밀번호를 로그에 남기지 않는다 (SEC-11).
 */
@Component
public class AdminSeeder {

  /** 시드 결과. 로그에는 이 이름만 남긴다 (값은 남기지 않는다, SEC-11). */
  public enum Outcome {
    CREATED,
    SKIPPED_NO_SECRET,
    SKIPPED_INVALID_EMAIL,
    SKIPPED_INVALID_PASSWORD,
    SKIPPED_EXISTS
  }

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;

  public AdminSeeder(UserRepository users, PasswordEncoder passwordEncoder, Clock clock) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
  }

  /**
   * 검사 순서: 시크릿 없음 → 이메일 형식 → 비밀번호 정책 → 이미 있음 → 생성.
   *
   * @param rawEmail 환경변수 값 그대로 (없으면 null·빈 값)
   * @param rawPassword 환경변수 값 그대로. 가입과 같이 앞뒤 공백을 제거하지 않는다.
   */
  public Outcome seed(String rawEmail, String rawPassword) {
    if (isBlank(rawEmail) || isBlank(rawPassword)) {
      return Outcome.SKIPPED_NO_SECRET;
    }
    String email = EmailRules.normalize(rawEmail);
    if (!EmailRules.isValid(email)) {
      return Outcome.SKIPPED_INVALID_EMAIL;
    }
    if (!PasswordRules.isValidForSignup(rawPassword)) {
      return Outcome.SKIPPED_INVALID_PASSWORD;
    }
    if (users.existsByEmail(email)) {
      return Outcome.SKIPPED_EXISTS;
    }
    User admin =
        User.create(
            email, passwordEncoder.encode(rawPassword), Role.ADMIN, LocalDateTime.now(clock));
    try {
      users.save(admin);
    } catch (DataIntegrityViolationException e) {
      // 확인과 저장 사이에 같은 이메일이 먼저 저장된 경우. 최종 판단은 DB 유일 제약이 한다 (가입과 같다).
      return Outcome.SKIPPED_EXISTS;
    }
    return Outcome.CREATED;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
