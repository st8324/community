package com.example.community.support;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

/**
 * 테스트 컨텍스트마다 JWT 서명 키를 무작위로 만든다 (03 문서 HD-19, CLAUDE.md 절대 규칙 5).
 *
 * <p>저장소에 키 문자열을 두지 않으므로 Gitleaks 예외가 필요 없다. 64바이트로 만들어 보안 테스트가 같은 키로 HS512 위조 토큰도 만들 수 있게 한다
 * (S-03). 운영 최소 길이(32바이트)보다 길다.
 */
public class RandomJwtSecretInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  public static final String PROPERTY = "community.jwt.secret";

  @Override
  public void initialize(ConfigurableApplicationContext context) {
    byte[] key = new byte[64];
    new SecureRandom().nextBytes(key);
    context
        .getEnvironment()
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "randomJwtSecret", Map.of(PROPERTY, Base64.getEncoder().encodeToString(key))));
  }
}
