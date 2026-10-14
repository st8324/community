package com.example.community.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 테스트용 MySQL. 운영과 같은 태그(mysql:8.4)로 고정한다 — latest 금지.
 *
 * <p>{@link ServiceConnection} 덕분에 접속 정보는 Spring Boot가 자동 설정한다 (TST-04).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

  public static final String MYSQL_IMAGE = "mysql:8.4";

  @Bean
  @ServiceConnection
  MySQLContainer mysql() {
    return new MySQLContainer(MYSQL_IMAGE);
  }
}
