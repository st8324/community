package com.example.community.harness;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.community.support.IntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 하네스 자체가 동작하는지 확인하는 스모크 테스트.
 *
 * <p>이 테스트가 실패하면 기능 테스트는 의미가 없다 — Docker, Testcontainers, Flyway, JPA 설정부터 확인한다.
 */
@IntegrationTest
class HarnessSmokeTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired Flyway flyway;

  @Test
  void 테스트_DB는_운영과_같은_MySQL_8_4다() {
    String version = jdbc.queryForObject("SELECT VERSION()", String.class);
    assertThat(version).startsWith("8.4");
  }

  @Test
  void Flyway_마이그레이션이_실패_없이_적용된다() {
    assertThat(flyway.info().pending()).isEmpty();
    assertThat(flyway.info().applied())
        .allSatisfy(m -> assertThat(m.getState().isFailed()).isFalse());
  }

  @Test
  void DB_세션_문자셋은_utf8mb4다() {
    String charset = jdbc.queryForObject("SELECT @@character_set_database", String.class);
    assertThat(charset).isEqualTo("utf8mb4");
  }
}
