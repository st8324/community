package com.example.community.admin;

import com.example.community.auth.UserRepository;
import java.time.Clock;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** 구현 전 뼈대 — 테스트가 컴파일은 되고 런타임에 실패하게 하는 용도다 (테스트 커밋). */
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

  public AdminSeeder(UserRepository users, PasswordEncoder passwordEncoder, Clock clock) {}

  public Outcome seed(String rawEmail, String rawPassword) {
    throw new UnsupportedOperationException("구현 전");
  }
}
