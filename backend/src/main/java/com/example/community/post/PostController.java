package com.example.community.post;

import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게시글 API (02 문서 5.3절). 컨트롤러는 DTO 매핑과 {@code @Valid}만 한다 — 권한 판단은 {@link PostService} (02 문서 5.2절).
 *
 * <p>로그인 필요 여부는 {@code SecurityConfig}의 URL 규칙이 먼저 막는다. 공개 조회 API에서 비회원이면 {@code jwt}가 null이다.
 */
@RestController
@RequestMapping("/api/posts")
public class PostController {

  private final PostService postService;

  public PostController(PostService postService) {
    this.postService = postService;
  }

  /** POST-01: 201 + 상세 형태 본문 + Location. */
  @PostMapping
  public ResponseEntity<PostResponse> create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PostRequest request) {
    PostResponse created = postService.create(userId(jwt), request);
    return ResponseEntity.created(URI.create("/api/posts/" + created.id())).body(created);
  }

  /** POST-03: 누구나. 요청자 기준 canEdit/canDelete. */
  @GetMapping("/{id}")
  public PostResponse get(@PathVariable("id") long id, @AuthenticationPrincipal Jwt jwt) {
    return postService.get(id, jwt == null ? null : userId(jwt));
  }

  /** sub는 디코더가 숫자임을 이미 검증했다 (JwtConfig). */
  private static long userId(Jwt jwt) {
    return Long.parseLong(jwt.getSubject());
  }
}
