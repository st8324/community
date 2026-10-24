package com.example.community.comment;

import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 댓글 API (02 문서 5.3절). 컨트롤러는 DTO 매핑과 {@code @Valid}만 한다 — 권한 판단은 {@link CommentService}.
 *
 * <p>로그인 필요 여부는 {@code SecurityConfig}의 URL 규칙이 먼저 막는다. 공개 조회 API에서 비회원이면 {@code jwt}가 null이다.
 */
@RestController
public class CommentController {

  private final CommentService commentService;

  public CommentController(CommentService commentService) {
    this.commentService = commentService;
  }

  /** CMT-02: 누구나. 요청자 기준 canDelete. */
  @GetMapping("/api/posts/{postId}/comments")
  public CommentListResponse list(
      @PathVariable("postId") long postId, @AuthenticationPrincipal Jwt jwt) {
    return commentService.list(postId, jwt == null ? null : userId(jwt));
  }

  /** CMT-01: 201 + 목록 항목과 같은 형태 본문. 입력 검증(@Valid, 400)이 서비스의 404 확인보다 먼저다 (D-29). */
  @PostMapping("/api/posts/{postId}/comments")
  public ResponseEntity<CommentResponse> create(
      @PathVariable("postId") long postId,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody CommentRequest request) {
    CommentResponse created = commentService.create(postId, userId(jwt), request);
    return ResponseEntity.created(URI.create("/api/posts/" + postId + "/comments")).body(created);
  }

  /** CMT-03: 204, 본문 없음. */
  @DeleteMapping("/api/comments/{id}")
  public ResponseEntity<Void> delete(
      @PathVariable("id") long id, @AuthenticationPrincipal Jwt jwt) {
    commentService.delete(id, userId(jwt));
    return ResponseEntity.noContent().build();
  }

  /** sub는 디코더가 숫자임을 이미 검증했다 (JwtConfig). */
  private static long userId(Jwt jwt) {
    return Long.parseLong(jwt.getSubject());
  }
}
