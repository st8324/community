package com.example.community.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * DATA-01 사용자. 컨트롤러 응답으로 직접 반환하지 않는다 (DATA-05, CLAUDE.md 3장).
 *
 * <p>{@code toString}을 정의하지 않는다 — 로그에 해시가 섞여 들어갈 통로를 만들지 않기 위해서다 (SEC-11).
 */
@Entity
@Table(name = "users")
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 255)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;

  // Hibernate 6.2+는 MySQL에서 STRING enum을 네이티브 ENUM 타입으로 기대한다 → 스키마 검증(validate)이
  // VARCHAR 컬럼과 어긋나지 않도록 JDBC 타입을 명시한다.
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 10)
  private Role role;

  /** UTC 시각 (application.yml의 hibernate.jdbc.time_zone=UTC, Clock은 UTC). */
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  protected User() {}

  private User(String email, String passwordHash, Role role, LocalDateTime createdAt) {
    this.email = email;
    this.passwordHash = passwordHash;
    this.role = role;
    this.createdAt = createdAt;
  }

  /**
   * @param normalizedEmail {@link EmailRules#normalize}를 거친 이메일
   * @param passwordHash {@code PasswordEncoder}로 만든 해시 (평문 금지)
   */
  public static User create(
      String normalizedEmail, String passwordHash, Role role, LocalDateTime createdAt) {
    return new User(normalizedEmail, passwordHash, role, createdAt);
  }

  public Long getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public Role getRole() {
    return role;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
