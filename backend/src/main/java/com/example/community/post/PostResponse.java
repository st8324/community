package com.example.community.post;

import com.example.community.auth.EmailMasker;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 게시글 상세 응답 — 작성(201)·상세(200)·수정(200) 공통 (01 D-23).
 *
 * <p>작성자는 마스킹된 표시명만 (A-1). 이메일 원문·작성자 id·role·삭제 정보는 담지 않는다 (DATA-05). 권한은 서버가 요청자 기준으로 계산한 불리언으로만
 * 준다 — 화면 표시용 힌트일 뿐이고 실제 허용 여부는 수정·삭제 요청 때 다시 확인한다 (SEC-07).
 *
 * <p>필드를 추가하기 전에 01 문서 DATA-05와 S-10을 확인한다.
 *
 * @param updatedAt 수정 전이면 null
 */
public record PostResponse(
    long id,
    String title,
    String content,
    String authorName,
    Instant createdAt,
    Instant updatedAt,
    boolean canEdit,
    boolean canDelete) {

  static PostResponse of(Post post, PostPermissions permissions) {
    return new PostResponse(
        post.getId(),
        post.getTitle(),
        post.getContent(),
        EmailMasker.mask(post.getAuthor().getEmail()),
        utc(post.getCreatedAt()),
        utc(post.getUpdatedAt()),
        permissions.canEdit(),
        permissions.canDelete());
  }

  /** DB에는 UTC로 저장한다 → 응답은 시간대가 붙은 {@code …Z} (01 D-23). */
  static Instant utc(LocalDateTime value) {
    return value == null ? null : value.toInstant(ZoneOffset.UTC);
  }
}
