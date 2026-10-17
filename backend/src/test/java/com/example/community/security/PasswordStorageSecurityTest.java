package com.example.community.security;

import static com.example.community.support.TestData.uniqueEmail;
import static com.example.community.support.TestData.validPassword;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.community.support.IntegrationTest;
import com.example.community.support.JsonBodies;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** S-08 (SEC-02), S-09 (SEC-03). */
@IntegrationTest
class PasswordStorageSecurityTest {

  static final String SMILE = "😀";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder passwordEncoder;

  ResultActions signup(String email, String password) throws Exception {
    return mvc.perform(
        post("/api/auth/signup")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(JsonBodies.of("email", email, "password", password)));
  }

  long countByEmail(String email) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Long.class, email);
  }

  /** S-08: DB에는 평문이 아니라 BCrypt 해시가 저장되고, 그 해시로 원래 비밀번호를 검증할 수 있다. */
  @Test
  void passwordIsStoredAsBcrypt() throws Exception {
    String email = uniqueEmail();
    String raw = validPassword();
    signup(email, raw).andExpect(status().isCreated());

    String stored =
        jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, email);
    assertThat(stored).doesNotContain(raw);
    assertThat(stored).matches("^\\{bcrypt}\\$2[aby]\\$\\d{2}\\$.{53}$");
    assertThat(passwordEncoder.matches(raw, stored)).isTrue();
  }

  /**
   * S-09: BCrypt는 72바이트 이후를 무시한다. 받아 주면 "앞 72바이트만 같은 다른 비밀번호"로도 로그인되므로 가입 단계에서 거부한다. 문자 수(64자 이하)는
   * 만족하지만 바이트가 넘치는 멀티바이트 입력으로 확인한다.
   */
  @Test
  void passwordOver72BytesIsRejected() throws Exception {
    String korean72 = uniqueEmail();
    String korean75 = uniqueEmail();
    String emoji72 = uniqueEmail();
    String emoji76 = uniqueEmail();

    signup(korean72, "가".repeat(24)).andExpect(status().isCreated());
    signup(korean75, "가".repeat(25))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields").value(hasItem("password")));
    signup(emoji72, SMILE.repeat(18)).andExpect(status().isCreated());
    signup(emoji76, SMILE.repeat(19)).andExpect(status().isBadRequest());

    assertThat(countByEmail(korean75)).isZero();
    assertThat(countByEmail(emoji76)).isZero();
  }
}
