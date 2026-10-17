package com.example.community.post;

import com.example.community.auth.EmailMasker;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 게시글 목록 응답 (POST-02 ⑤, 01 D-21). 항목에는 본문과 권한 불리언을 넣지 않는다 — 목록에서는 쓰지 않고, 응답이 커진다.
 *
 * <p>Spring Data의 {@code Page}를 그대로 직렬화하지 않는다: 내부 구조(pageable, sort 등)가 그대로 나가고, 버전에 따라 모양이 바뀐다.
 */
public record PostListResponse(
    List<Item> items, int page, int size, long totalElements, int totalPages) {

  /** 목록 항목. 작성자는 마스킹된 표시명만 (A-1, DATA-05). */
  public record Item(long id, String title, String authorName, Instant createdAt) {

    static Item of(Post post) {
      return new Item(
          post.getId(),
          post.getTitle(),
          EmailMasker.mask(post.getAuthor().getEmail()),
          PostResponse.utc(post.getCreatedAt()));
    }
  }

  static PostListResponse of(Page<Post> page) {
    return new PostListResponse(
        page.getContent().stream().map(Item::of).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }

  /** 조회 없이 "마지막 페이지를 넘었다"는 응답 (int 범위를 넘는 시작 위치, PostService.list 참고). */
  static PostListResponse beyondLastPage(int page, int size, long totalElements) {
    int totalPages = (int) ((totalElements + size - 1) / size);
    return new PostListResponse(List.of(), page, size, totalElements, totalPages);
  }
}
