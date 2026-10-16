package com.example.community.common.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * JWT 서명·검증 (SEC-04, 02 문서 3.5절). 서명·만료 검증은 Nimbus/Spring Security가 하고, 여기서는 "설정"만 한다.
 *
 * <ul>
 *   <li>키: Base64 디코딩 후 32바이트(256비트) 이상. 없거나 짧으면 앱이 뜨지 않는다 — 약한 키로 조용히 운영되는 것보다 낫다.
 *   <li>디코더 알고리즘을 HS256으로 고정 → {@code alg=none}, HS384/512 등 다른 알고리즘 토큰 거부.
 *   <li>{@code exp} 필수, 허용 오차 0, 시각은 주입된 Clock. {@code sub}는 숫자만 (사용자 id).
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

  static final int MIN_KEY_BYTES = 32;

  @Bean
  SecretKey jwtSigningKey(JwtProperties properties) {
    String secret = properties.secret();
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException(
          "JWT_SECRET이 비어 있다 (SEC-04). 예: openssl rand -base64 32 결과를 환경변수로 넣는다.");
    }
    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(secret.strip());
    } catch (IllegalArgumentException e) {
      // 원인 예외를 붙이지 않는다: 메시지에 키의 문자 일부가 들어 있다 (SEC-11).
      throw new IllegalStateException("JWT_SECRET이 Base64 형식이 아니다 (SEC-04).");
    }
    if (bytes.length < MIN_KEY_BYTES) {
      throw new IllegalStateException(
          "JWT_SECRET이 너무 짧다: 디코딩 후 " + MIN_KEY_BYTES + "바이트 이상이어야 한다 (SEC-04).");
    }
    return new SecretKeySpec(bytes, "HmacSHA256");
  }

  @Bean
  JwtDecoder jwtDecoder(SecretKey jwtSigningKey, Clock clock) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build();

    JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
    timestamps.setClock(clock);
    JwtClaimValidator<Instant> expRequired =
        new JwtClaimValidator<>(JwtClaimNames.EXP, Objects::nonNull);
    JwtClaimValidator<String> numericSubject =
        new JwtClaimValidator<>(JwtClaimNames.SUB, sub -> sub != null && sub.matches("\\d{1,18}"));

    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(timestamps, expRequired, numericSubject));
    return decoder;
  }

  @Bean
  JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
    return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(jwtSigningKey));
  }
}
