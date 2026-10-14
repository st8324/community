package com.example.community.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * API 통합 테스트용 메타 어노테이션.
 *
 * <p>실제 MySQL(Testcontainers) + 전체 Security Filter Chain + MockMvc. 모든 기능 AC 테스트와 권한 매트릭스·보안 회귀
 * 테스트는 이 어노테이션을 붙인다. 설정이 한 곳에 모여 있어야 Spring 컨텍스트가 재사용되어 테스트가 빨라진다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
public @interface IntegrationTest {}
