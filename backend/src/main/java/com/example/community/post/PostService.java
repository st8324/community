package com.example.community.post;

import com.example.community.auth.User;
import com.example.community.auth.UserRepository;
import com.example.community.common.error.ApiException;
import com.example.community.common.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
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
