package com.example.community.auth;

import com.example.community.common.error.ApiException;
import com.example.community.common.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** 회원가입·로그인 규칙. 비밀번호 평문은 이 클래스 밖으로 나가지 않고, 로그에도 남기지 않는다 (SEC-11). */
@Service
public class AuthService {

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;

  public AuthService(UserRepository users, PasswordEncoder passwordEncoder, Clock clock) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
  }

  /**
   * AUTH-01. 역할은 항상 USER (요청으로 정할 수 없음).
   *
   * @throws ApiException {@code EMAIL_ALREADY_USED} — 중복 (409)
   */
  public void signup(SignupRequest request) {
    if (users.existsByEmail(request.email())) {
      throw new ApiException(ErrorCode.EMAIL_ALREADY_USED);
    }
    String hash = passwordEncoder.encode(request.password());
    User user = User.create(request.email(), hash, Role.USER, LocalDateTime.now(clock));
    try {
      users.save(user);
    } catch (DataIntegrityViolationException e) {
      // 위 exists 확인과 저장 사이에 같은 이메일이 먼저 저장된 경우(동시 요청). 최종 판단은 DB 유일 제약이 한다.
      throw new ApiException(ErrorCode.EMAIL_ALREADY_USED);
    }
  }
}
