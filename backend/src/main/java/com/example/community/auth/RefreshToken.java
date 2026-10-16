package com.example.community.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** DATA-06 리프레시 토큰. 원문은 저장하지 않는다 (SEC-16). 시각은 UTC. */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "family_id", nullable = false, length = 36)
  private String familyId;

  @Column(name = "token_hash", nullable = false, length = 64)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "revoked_at")
  private LocalDateTime revokedAt;

  protected RefreshToken() {}

  RefreshToken(
      long userId,
      String familyId,
      String tokenHash,
      LocalDateTime expiresAt,
      LocalDateTime createdAt) {
    this.userId = userId;
    this.familyId = familyId;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
    this.createdAt = createdAt;
  }

  boolean isRevoked() {
    return revokedAt != null;
  }

  boolean isExpiredAt(LocalDateTime now) {
    return !expiresAt.isAfter(now);
  }

  void revoke(LocalDateTime now) {
    this.revokedAt = now;
  }

  Long getUserId() {
    return userId;
  }

  String getFamilyId() {
    return familyId;
  }

  LocalDateTime getExpiresAt() {
    return expiresAt;
  }
}
