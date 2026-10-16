package com.example.community.common.time;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 현재 시각은 항상 이 Clock으로 얻는다 (UTC). 토큰 만료 등 시간 의존 로직을 테스트에서 고정할 수 있게 한다 (CLAUDE.md 4장). */
@Configuration
public class ClockConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
