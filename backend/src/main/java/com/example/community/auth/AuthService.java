package com.example.community.auth;

import com.example.community.common.error.ApiException;
import com.example.community.common.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** 회원가입·로그인 규칙. 비밀번호 평문은 이 클래스 밖으로 나가지 않고, 로그에도 남기지 않는다 (SEC-11). */
@Service
public class AuthService {

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;

  /**
   * 없는 이메일로 로그인할 때 비교에 쓰는 가짜 해시. 실제 해시와 같은 알고리즘·비용이라 비교 시간이 비슷하다 → 응답 시간으로 계정 존재 여부를 추측하기 어렵게 한다
   * (AUTH-02 ②). 기동마다 새로 만들며 어떤 비밀번호와도 맞지 않는다.
   */
  private final String dummyHash;

  public AuthService(UserRepository users, PasswordEncoder passwordEncoder, Clock clock) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
    this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
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

  /**
   * AUTH-02. 이메일이 없든 비밀번호가 틀리든 같은 예외 (②).
   *
   * @throws ApiException {@code INVALID_CREDENTIALS} (401)
   */
  public User login(LoginRequest request) {
    Optional<User> user = users.findByEmail(request.email());
    String hash = user.map(User::getPasswordHash).orElse(dummyHash);
    boolean matches = passwordEncoder.matches(request.password(), hash);
    if (user.isEmpty() || !matches) {
      throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
    }
    return user.get();
  }

  /**
   * AUTH-05. 서명은 유효하지만 사용자가 없는 토큰(이론상 회원 탈퇴 등)은 비로그인으로 본다.
   *
   * @throws ApiException {@code UNAUTHORIZED} (401)
   */
  public User currentUser(long userId) {
    return users.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
  }
}
