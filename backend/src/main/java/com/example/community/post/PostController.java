package com.example.community.post;

import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

  /**
   * POST-02: 누구나. {@code page}는 0부터, 생략·빈 값이면 0. 숫자가 아니면 형 변환 실패 → 400 {@code INVALID_REQUEST}. 크기
   * 파라미터는 받지 않는다 (01 D-21).
   */
  @GetMapping
  public PostListResponse list(@RequestParam(name = "page", defaultValue = "0") int page) {
    return postService.list(page);
  }

  /** POST-03: 누구나. 요청자 기준 canEdit/canDelete. */
  @GetMapping("/{id}")
  public PostResponse get(@PathVariable("id") long id, @AuthenticationPrincipal Jwt jwt) {
    return postService.get(id, jwt == null ? null : userId(jwt));
  }

  /** POST-04: 200 + 상세 형태. 입력 검증(@Valid, 400)이 서비스의 404·403 확인보다 먼저다 (01 D-24). */
  @PutMapping("/{id}")
  public PostResponse update(
      @PathVariable("id") long id,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody PostRequest request) {
    return postService.update(id, userId(jwt), request);
  }

  /** POST-05: 204, 본문 없음. */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @PathVariable("id") long id, @AuthenticationPrincipal Jwt jwt) {
    postService.delete(id, userId(jwt));
    return ResponseEntity.noContent().build();
  }

  /** sub는 디코더가 숫자임을 이미 검증했다 (JwtConfig). */
  private static long userId(Jwt jwt) {
    return Long.parseLong(jwt.getSubject());
  }
}
