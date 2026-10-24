package com.example.community.comment;

import com.example.community.common.validation.TextLength;

/**
 * 댓글 작성 요청 (CMT-01). 받는 필드는 content뿐이다. 요청에 {@code authorId}·{@code postId} 등이 와도 바인딩할 곳이 없어 무시된다
 * (대량 할당 방지, CMT-01 ③). 앞뒤 공백은 유니코드 기준으로 제거하고(strip), 검증과 저장 모두 제거된 값을 쓴다 (01 D-28).
 */
public record CommentRequest(
    @TextLength(min = 1, max = CommentRequest.CONTENT_MAX) String content) {

  /** A-3 (확인 필요 항목 — 바뀌면 여기와 V4 마이그레이션의 VARCHAR(1000)을 함께 바꾼다). */
  public static final int CONTENT_MAX = 1_000;

  public CommentRequest {
    content = content == null ? null : content.strip();
  }
}
