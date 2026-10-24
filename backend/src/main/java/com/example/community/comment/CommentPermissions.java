package com.example.community.comment;

import com.example.community.auth.Role;
import com.example.community.auth.User;

/**
 * 댓글 삭제 권한 규칙 (01 문서 3.1 매트릭스, CMT-02 ④, CMT-03). 화면 힌트({@code canDelete})와 실제 삭제 인가가 <b>같은 규칙</b>을
 * 쓰도록 한 곳에 둔다.
 *
 * <ul>
 *   <li>삭제: 댓글 작성자 본인 또는 관리자. 글 작성자라도 타인의 댓글은 지울 수 없다.
 *   <li>비회원(viewer == null): false.
 * </ul>
 *
 * <p>관리자 여부는 토큰이 아니라 DB에서 읽은 {@link User#getRole()}로 판단한다 (02 문서 T-15).
 */
final class CommentPermissions {

  private CommentPermissions() {}

  static boolean canDelete(Comment comment, User viewer) {
    if (viewer == null) {
      return false;
    }
    return comment.isWrittenBy(viewer.getId()) || viewer.getRole() == Role.ADMIN;
  }
}
