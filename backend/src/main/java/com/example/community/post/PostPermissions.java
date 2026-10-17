package com.example.community.post;

import com.example.community.auth.Role;
import com.example.community.auth.User;

/**
 * 게시글 권한 규칙 (01 문서 3.1 매트릭스, POST-03 ③, POST-04 ①, POST-05). 화면 힌트({@code canEdit}/{@code
 * canDelete})와 실제 수정·삭제 인가가 <b>같은 규칙</b>을 쓰도록 한 곳에 둔다 — 둘이 따로 구현되면 "버튼은 보이는데 403" 같은 어긋남이 생긴다.
 *
 * <ul>
 *   <li>수정: 작성자 본인만. 관리자도 타인 글은 수정할 수 없다 (A-6).
 *   <li>삭제: 작성자 본인 또는 관리자.
 *   <li>비회원(viewer == null): 둘 다 false.
 * </ul>
 *
 * <p>관리자 여부는 토큰이 아니라 DB에서 읽은 {@link User#getRole()}로 판단한다 (02 문서 T-15).
 */
record PostPermissions(boolean canEdit, boolean canDelete) {

  static PostPermissions of(Post post, User viewer) {
    if (viewer == null) {
      return new PostPermissions(false, false);
    }
    boolean isAuthor = post.isWrittenBy(viewer.getId());
    boolean isAdmin = viewer.getRole() == Role.ADMIN;
    return new PostPermissions(isAuthor, isAuthor || isAdmin);
  }
}
