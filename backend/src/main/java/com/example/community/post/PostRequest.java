package com.example.community.post;

import com.example.community.common.validation.TextLength;

/**
 * 게시글 작성·수정 요청 (POST-01, POST-04 ②).
 *
 * <p>받는 필드는 title·content뿐이다. 요청에 {@code authorId}·{@code id} 등이 와도 바인딩할 곳이 없어 무시된다 (대량 할당 방지,
 * POST-01 ③). 앞뒤 공백은 생성자에서 유니코드 기준으로 제거하고(strip), 검증과 저장 모두 제거된 값을 쓴다 (01 D-22).
 */
public record PostRequest(
    @TextLength(min = 1, max = PostRequest.TITLE_MAX) String title,
    @TextLength(min = 1, max = PostRequest.CONTENT_MAX) String content) {

  /** A-3 (확인 필요 항목 — 바뀌면 여기와 V3 마이그레이션의 VARCHAR(100)을 함께 바꾼다). */
  public static final int TITLE_MAX = 100;

  public static final int CONTENT_MAX = 10_000;

  public PostRequest {
    title = title == null ? null : title.strip();
    content = content == null ? null : content.strip();
  }
}
