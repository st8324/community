package com.example.community.admin;

import com.example.community.admin.AdminSeeder.Outcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 앱 기동 시 관리자 시드를 실행한다 (ADM-01 ⑥, 02 T-24). 시드가 실패해도 앱 기동은 막지 않는다 — 예외를 삼키되 <b>원인 종류(클래스 이름)만</b> 경고
 * 로그에 남긴다. 예외 메시지에는 입력값이 섞일 수 있으므로 메시지와 스택트레이스는 남기지 않는다 (SEC-11).
 *
 * <p>이메일·비밀번호는 환경변수 {@code ADMIN_EMAIL}·{@code ADMIN_PASSWORD}로만 받는다 (application.yml의 {@code
 * community.admin.*}). 값은 로그·문자열 표현 어디에도 넣지 않는다.
 */
@Component
public class AdminSeedRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(AdminSeedRunner.class);

  private final AdminSeeder seeder;
  private final String email;
  private final String password;

  public AdminSeedRunner(
      AdminSeeder seeder,
      @Value("${community.admin.email:}") String email,
      @Value("${community.admin.password:}") String password) {
    this.seeder = seeder;
    this.email = email;
    this.password = password;
  }

  @Override
  public void run(ApplicationArguments args) {
    try {
      Outcome outcome = seeder.seed(email, password);
      if (outcome == Outcome.CREATED) {
        log.info("관리자 시드: 관리자 계정을 만들었습니다.");
      } else {
        log.warn("관리자 시드를 건너뜀: {}", outcome);
      }
    } catch (RuntimeException e) {
      log.warn("관리자 시드를 건너뜀: 예외 종류 {}", e.getClass().getSimpleName());
    }
  }
}
