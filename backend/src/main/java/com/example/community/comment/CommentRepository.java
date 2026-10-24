package com.example.community.comment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 파생 쿼리만 사용한다 — Spring Data가 파라미터 바인딩으로 만든다 (SEC-09). 조회 메서드는 이름에 {@code DeletedAtIsNull}을 붙여 삭제된
 * 댓글을 돌려주지 않는다 (DATA-03). 기본 {@code findById}는 삭제된 댓글도 찾으므로 서비스에서 쓰지 않는다.
 */
public interface CommentRepository extends JpaRepository<Comment, Long> {

  /** 목록 (CMT-02 ②). 작성순(오래된 순), 같은 시각이면 id 순. 작성자를 함께 읽어 항목마다 사용자 조회가 반복되지 않게 한다. */
  @EntityGraph(attributePaths = "author")
  List<Comment> findByPostIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(Long postId);

  /** 삭제 대상. */
  @EntityGraph(attributePaths = "author")
  Optional<Comment> findByIdAndDeletedAtIsNull(Long id);
}
