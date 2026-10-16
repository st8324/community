package com.example.community.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 로그인 요청 (AUTH-02). 이메일은 가입 때와 같은 규칙으로 정규화한다 (④).
 *
 * <p>로그인에서는 이메일 "형식"을 검사하지 않는다 — 형식이 틀린 이메일은 어차피 계정이 없으므로 401과 같은 결과다. 대신 빈 값과 상한 초과만 400으로 거른다
 * (⑤): 큰 입력이 BCrypt까지 가지 않게.
 *
 * @param rememberMe 선택. true면 로그인 상태 유지(AUTH-02 ⑧, AUTH-06). 없거나 null이면 false
 */
public record LoginRequest(
    @NotBlank @Size(max = EmailRules.MAX_LENGTH) String email,
    @ValidPassword(forSignup = false) String password,
    Boolean rememberMe) {

  public LoginRequest {
    email = EmailRules.normalize(email);
  }

  public boolean keepLoggedIn() {
    return Boolean.TRUE.equals(rememberMe);
  }
}
