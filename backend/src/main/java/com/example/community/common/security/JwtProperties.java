package com.example.community.common.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code community.jwt.*} 설정. secret은 환경변수 {@code JWT_SECRET}으로만 주입한다 (SEC-04, 02 문서 5.5절).
 *
 * <p>레코드의 기본 toString은 모든 값을 출력한다 → 설정 객체가 로그·오류 메시지에 찍혀도 키가 새지 않도록 덮어쓴다 (SEC-11).
 */
@ConfigurationProperties("community.jwt")
public record JwtProperties(String secret, Duration ttl) {

  @Override
  public String toString() {
    return "JwtProperties[secret=****, ttl=" + ttl + "]";
  }
}
