package com.example.community.auth;

/**
 * 로그인·현재 사용자 응답 (AUTH-02 ①, AUTH-05 ①). id와 마스킹된 표시명만 담는다 — 이메일 원문·role·해시 금지 (DATA-05).
 *
 * <p>필드를 추가하기 전에 01 문서 DATA-05와 S-10을 확인한다.
 */
public record MeResponse(long id, String displayName) {

  public static MeResponse from(User user) {
    return new MeResponse(user.getId(), EmailMasker.mask(user.getEmail()));
  }
}
