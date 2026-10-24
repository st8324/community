package com.example.community.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 구현 전 뼈대 — 아무것도 하지 않는다 (테스트 커밋). */
@Component
public class AdminSeedRunner implements ApplicationRunner {

  public AdminSeedRunner(
      AdminSeeder seeder,
      @Value("${community.admin.email:}") String email,
      @Value("${community.admin.password:}") String password) {}

  @Override
  public void run(ApplicationArguments args) {}
}
