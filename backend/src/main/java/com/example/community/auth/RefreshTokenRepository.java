package com.example.community.auth;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 파생 쿼리·JPQL 이름 파라미터만 사용한다 (SEC-09). */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

  /**
   * 교체용 조회: 행을 잠근다 (SELECT ... FOR UPDATE). 같은 토큰으로 동시에 두 요청이 와도 하나만 교체에 성공하고, 다른 하나는 "이미 쓴 토큰"으로
   * 판정된다.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<RefreshToken> findForUpdateByTokenHash(String tokenHash);

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  /** 묶음 전체 폐기 (재사용 감지, 로그아웃). 이미 폐기된 행의 시각은 바꾸지 않는다. */
  @Modifying
  @Query(
      "update RefreshToken t set t.revokedAt = :now"
          + " where t.familyId = :familyId and t.revokedAt is null")
  int revokeFamily(@Param("familyId") String familyId, @Param("now") LocalDateTime now);
}
