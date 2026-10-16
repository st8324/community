package com.example.community.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code community.refresh.ttl}: 로그인 유지 기간 (A-7, 기본 14일, 절대 만료). 비밀 값이 아니다. */
@ConfigurationProperties("community.refresh")
public record RefreshTokenProperties(Duration ttl) {}
