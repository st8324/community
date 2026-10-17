package com.example.community.post;

import com.example.community.auth.User;
import com.example.community.auth.UserRepository;
import com.example.community.common.error.ApiException;
import com.example.community.common.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시글 규칙 (01 문서 4.2절). 인가(소유자·관리자 확인)는 반드시 이 계층에서 한다 (SEC-07, 02 문서 5.2절).
 *
 * <p>요청자는 토큰의 사용자 id만 받아 DB에서 다시 읽는다 — 역할을 토큰에서 믿지 않는다 (02 문서 T-15). 응답 변환도 트랜잭션 안에서 끝낸다 ({@code
 * open-in-view: false}라 밖에서는 지연 로딩이 안 된다).
 */
@Service
public class PostService {

  /** A-4 (확인 필요 항목). 클라이언트 입력으로 바꿀 수 없다 (01 D-21). */
  static final int PAGE_SIZE = 20;

  /** POST-02 ① 최신순. 같은 시각이면 나중에 저장된(id가 큰) 글이 먼저. 인덱스 ix_posts_list와 같은 순서. */
  static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

  private final PostRepository posts;
  private final UserRepository users;
  private final Clock clock;

  public PostService(PostRepository posts, UserRepository users, Clock clock) {
    this.posts = posts;
    this.users = users;
    this.clock = clock;
  }

  /** POST-01. 작성자는 항상 로그인한 사용자 (요청 본문으로 정할 수 없음). */
  @Transactional
  public PostResponse create(long userId, PostRequest request) {
    User author = requireUser(userId);
    Post post =
        posts.save(
            Post.create(author, request.title(), request.content(), LocalDateTime.now(clock)));
    return PostResponse.of(post, PostPermissions.of(post, author));
  }

  /**
   * POST-03. 없는 글과 삭제된 글은 똑같이 404.
   *
   * @param viewerId 비회원이면 null
   */
  @Transactional(readOnly = true)
  public PostResponse get(long postId, Long viewerId) {
    Post post = requirePost(postId);
    User viewer = viewerId == null ? null : requireUser(viewerId);
    return PostResponse.of(post, PostPermissions.of(post, viewer));
  }

  /**
   * POST-02. 크기는 서버 고정(클라이언트가 바꿀 수 없음), 최신순(작성 시각 → id 내림차순). 범위를 넘는 페이지는 빈 목록.
   *
   * @throws ApiException {@code VALIDATION_FAILED}, fields=["page"] — 음수 (01 D-21)
   */
  @Transactional(readOnly = true)
  public PostListResponse list(int page) {
    if (page < 0) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, List.of("page"));
    }
    if ((long) page * PAGE_SIZE > Integer.MAX_VALUE) {
      // JPA의 시작 위치(firstResult)는 int다. 그대로 넘기면 예외 → 500이 된다. 이런 페이지는 어차피 마지막을 넘으므로 빈 목록 (POST-02
      // ④).
      return PostListResponse.beyondLastPage(page, PAGE_SIZE, posts.countByDeletedAtIsNull());
    }
    PageRequest request = PageRequest.of(page, PAGE_SIZE, NEWEST_FIRST);
    return PostListResponse.of(posts.findByDeletedAtIsNull(request));
  }

  private Post requirePost(long postId) {
    return posts
        .findByIdAndDeletedAtIsNull(postId)
        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
  }

  /** 서명은 유효하지만 사용자가 없는 토큰은 비로그인으로 본다 (AUTH-05와 같은 처리). */
  private User requireUser(long userId) {
    return users.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
  }
}
