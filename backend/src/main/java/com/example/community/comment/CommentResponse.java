package com.example.community.comment;

import com.example.community.auth.EmailMasker;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * 댓글 응답 — 작성(201)과 목록 항목 공통 (01 D-27). 작성자는 마스킹된 표시명만 (A-1). 이메일 원문·작성자 id·role·삭제 정보는 담지 않는다
 * (DATA-05). {@code canDelete}는 서버가 요청자 기준으로 계산한 표시용 힌트이고, 실제 허용 여부는 삭제 요청 때 다시 확인한다 (SEC-07).
 *
 * <p>필드를 추가하기 전에 01 문서 DATA-05와 S-10을 확인한다.
 */
public record CommentResponse(
    long id, String content, String authorName, Instant createdAt, boolean canDelete) {

  static CommentResponse of(Comment comment, boolean canDelete) {
    return new CommentResponse(
        comment.getId(),
        comment.getContent(),
        EmailMasker.mask(comment.getAuthor().getEmail()),
        comment.getCreatedAt().toInstant(ZoneOffset.UTC),
        canDelete);
  }
}
