package com.example.community.auth;

/**
 * 회원가입 요청 (AUTH-01).
 *
 * <p>받는 필드는 email·password 둘뿐이다. 요청에 {@code role} 등이 와도 바인딩할 곳이 없어 무시된다 (AUTH-01 ④, 대량 할당 방지). 이메일은
 * 생성자에서 정규화한 뒤 검증한다 → "앞뒤 공백 + 대문자" 입력도 같은 규칙으로 판단된다. 비밀번호는 손대지 않는다.
 */
public record SignupRequest(
    @ValidEmail String email, @ValidPassword(forSignup = true) String password) {

  public SignupRequest {
    email = EmailRules.normalize(email);
  }
}
