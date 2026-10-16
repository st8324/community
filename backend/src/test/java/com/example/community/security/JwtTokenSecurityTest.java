package com.example.community.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.community.auth.Role;
import com.example.community.support.AuthFixtures;
import com.example.community.support.AuthFixtures.Account;
import com.example.community.support.IntegrationTest;
import com.example.community.support.RandomJwtSecretInitializer;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * S-01~S-04 (SEC-04, SEC-06). 테스트가 서버와 같은 무작위 키로 토큰을 직접 만든다.
 *
 * <p>먼저 "올바르게 만든 토큰은 통과한다"는 대조군을 둔다. 대조군이 없으면 위조 토큰이 401인 이유가 "검증이 잘 돼서"인지 "토큰 만드는 코드가 틀려서"인지 구분할 수
 * 없다.
 */
@IntegrationTest
class JwtTokenSecurityTest {

  @Autowired MockMvc mvc;
  @Autowired AuthFixtures fixtures;
  @Autowired Clock clock;

  @Value("${" + RandomJwtSecretInitializer.PROPERTY + "}")
  String secretBase64;

  ResultActions callMe(String token) throws Exception {
    return mvc.perform(get("/api/auth/me").cookie(new Cookie(AuthFixtures.ACCESS_TOKEN, token)));
  }

  JWTClaimsSet claims(long userId, Instant issuedAt, Instant expiresAt) {
    JWTClaimsSet.Builder b = new JWTClaimsSet.Builder().subject(Long.toString(userId));
    if (issuedAt != null) {
      b.issueTime(Date.from(issuedAt));
    }
    if (expiresAt != null) {
      b.expirationTime(Date.from(expiresAt));
    }
    return b.build();
  }

  JWTClaimsSet validClaims(long userId) {
    Instant now = clock.instant();
    return claims(userId, now, now.plus(Duration.ofHours(1)));
  }

  String sign(JWSAlgorithm alg, JWTClaimsSet claims) throws Exception {
    SignedJWT jwt = new SignedJWT(new JWSHeader(alg), claims);
    jwt.sign(new MACSigner(Base64.getDecoder().decode(secretBase64)));
    return jwt.serialize();
  }

  @Test
  void 대조군_서버_키로_HS256_서명한_토큰은_통과한다() throws Exception {
    Account account = fixtures.createUser(Role.USER);
    callMe(sign(JWSAlgorithm.HS256, validClaims(account.id()))).andExpect(status().isOk());
  }

  /** S-01: 서명이 없는 토큰({@code alg=none})을 "검증 생략"으로 받아 주면 누구나 아무 사용자로 위장할 수 있다. */
  @Test
  void algNoneTokenIsRejected() throws Exception {
    Account account = fixtures.createUser(Role.USER);
    String unsigned = new PlainJWT(validClaims(account.id())).serialize();
    callMe(unsigned).andExpect(status().isUnauthorized());
  }

  /** S-02: 정상 토큰의 내용(sub)만 다른 사용자로 바꾸고 서명은 그대로 둔다. */
  @Test
  void tamperedSignatureIsRejected() throws Exception {
    Account victim = fixtures.createUser(Role.ADMIN);
    Account attacker = fixtures.createUser(Role.USER);
    String[] parts = sign(JWSAlgorithm.HS256, validClaims(attacker.id())).split("\\.");
    String forgedPayload = Base64URL.encode(validClaims(victim.id()).toString()).toString();

    callMe(parts[0] + "." + forgedPayload + "." + parts[2]).andExpect(status().isUnauthorized());
  }

  /** S-03: 키가 같아도 HS256이 아니면 거부한다 (디코더 알고리즘 고정 → 알고리즘 혼동 공격 차단). */
  @Test
  void otherAlgorithmIsRejected() throws Exception {
    Account account = fixtures.createUser(Role.USER);
    callMe(sign(JWSAlgorithm.HS384, validClaims(account.id())))
        .andExpect(status().isUnauthorized());
    callMe(sign(JWSAlgorithm.HS512, validClaims(account.id())))
        .andExpect(status().isUnauthorized());
  }

  /** S-04: 만료 시각이 지났거나, 아예 없는 토큰은 거부한다. 서버 시각은 주입된 Clock 기준이다. */
  @Test
  void expiredTokenIsRejected() throws Exception {
    Account account = fixtures.createUser(Role.USER);
    Instant now = clock.instant();

    String expired =
        sign(
            JWSAlgorithm.HS256,
            claims(account.id(), now.minus(Duration.ofHours(2)), now.minusSeconds(60)));
    callMe(expired).andExpect(status().isUnauthorized());

    String noExpiry = sign(JWSAlgorithm.HS256, claims(account.id(), now, null));
    callMe(noExpiry).andExpect(status().isUnauthorized());
  }

  @Test
  void sub가_숫자가_아닌_토큰은_401이다() throws Exception {
    Instant now = clock.instant();
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject("admin")
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(Duration.ofHours(1))))
            .build();
    callMe(sign(JWSAlgorithm.HS256, claims)).andExpect(status().isUnauthorized());
  }

  @Test
  void 서명은_맞지만_존재하지_않는_사용자의_토큰은_401이다() throws Exception {
    callMe(sign(JWSAlgorithm.HS256, validClaims(999_999_999_999L)))
        .andExpect(status().isUnauthorized());
  }
}
