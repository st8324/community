package com.example.community.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 파생 쿼리만 사용한다 — Spring Data가 파라미터 바인딩으로 만든다 (SEC-09). */
public interface UserRepository extends JpaRepository<User, Long> {

  /**
   * @param normalizedEmail 반드시 {@link EmailRules#normalize}를 거친 값
   */
  Optional<User> findByEmail(String normalizedEmail);

  /**
   * @param normalizedEmail 반드시 {@link EmailRules#normalize}를 거친 값
   */
  boolean existsByEmail(String normalizedEmail);
}
