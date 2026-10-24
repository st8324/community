package com.example.community.comment;

import com.example.community.auth.User;
import com.example.community.auth.UserRepository;
import com.example.community.common.error.ApiException;
import com.example.community.common.error.ErrorCode;
import com.example.community.post.PostRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 댓글 규칙 (01 문서 4.2절). 인가(작성자·관리자 확인)는 반드시 이 계층에서 한다 (SEC-07, 02 문서 5.2절).
 *
 * <p>요청자는 토큰의 사용자 id만 받아 DB에서 다시 읽는다 (T-15). 글이 없거나 삭제됐으면 댓글도 없는 것으로 본다 — 글 삭제 때 댓글을 연쇄 삭제하지 않고
 * <b>조회 시점에 글 상태를 확인</b>한다 (01 D-31, POST-05 AC④).
 */
@Service
public class CommentService {

  private final CommentRepository comments;
  private final PostRepository posts;
  private final UserRepository users;
  private final Clock clock;

  public CommentService(
      CommentRepository comments, PostRepository posts, UserRepository users, Clock clock) {
    this.comments = comments;
    this.posts = posts;
    this.users = users;
    this.clock = clock;
  }

  /**
   * CMT-01. 검사 순서: (401·400은 앞 단계) → 404 글 없음·삭제됨 (D-29). 작성자는 항상 로그인한 사용자, 글은 경로의 id뿐이다.
   *
   * @throws ApiException {@code UNAUTHORIZED}, {@code NOT_FOUND}
   */
  @Transactional
  public CommentResponse create(long postId, long userId, CommentRequest request) {
    User author = requireUser(userId);
    requirePost(postId);
    Comment comment =
        comments.save(Comment.create(postId, author, request.content(), LocalDateTime.now(clock)));
    return CommentResponse.of(comment, CommentPermissions.canDelete(comment, author));
  }

  /**
   * CMT-02. 글이 없거나 삭제됐으면 404 (삭제된 글의 댓글은 조회되지 않는다). 삭제된 댓글은 제외, 오래된 순.
   *
   * @param viewerId 비회원이면 null
   */
  @Transactional(readOnly = true)
  public CommentListResponse list(long postId, Long viewerId) {
    requirePost(postId);
    User viewer = viewerId == null ? null : requireUser(viewerId);
    return new CommentListResponse(
        comments.findByPostIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(postId).stream()
            .map(c -> CommentResponse.of(c, CommentPermissions.canDelete(c, viewer)))
            .toList());
  }

  /**
   * CMT-03. 소프트 삭제 + 삭제자 기록 (ADM-03). 검사 순서: (401은 앞 단계) → 404 댓글 없음·이미 삭제됨·소속 글 삭제됨 → 403 권한 없음
   * (D-29).
   *
   * @throws ApiException {@code NOT_FOUND}, {@code FORBIDDEN}
   */
  @Transactional
  public void delete(long commentId, long userId) {
    Comment comment =
        comments
            .findByIdAndDeletedAtIsNull(commentId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    requirePost(comment.getPostId());
    User user = requireUser(userId);
    if (!CommentPermissions.canDelete(comment, user)) {
      throw new ApiException(ErrorCode.FORBIDDEN);
    }
    comment.softDelete(user.getId(), LocalDateTime.now(clock));
  }

  private void requirePost(long postId) {
    if (posts.findByIdAndDeletedAtIsNull(postId).isEmpty()) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
  }

  /** 서명은 유효하지만 사용자가 없는 토큰은 비로그인으로 본다 (AUTH-05와 같은 처리). */
  private User requireUser(long userId) {
    return users.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
  }
}
