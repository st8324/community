package com.example.community.post;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 파생 쿼리만 사용한다 — Spring Data가 파라미터 바인딩으로 만든다 (SEC-09).
 *
 * <p>조회 메서드는 이름에 {@code DeletedAtIsNull}을 붙여 삭제된 글을 돌려주지 않는다 (DATA-03). 기본 {@code findById}는 삭제된 글도
 * 찾으므로 서비스에서 쓰지 않는다.
 */
public interface PostRepository extends JpaRepository<Post, Long> {

  /** 상세·수정·삭제 대상. 작성자를 함께 읽는다 (표시명 계산, 지연 로딩 추가 쿼리 방지). */
  @EntityGraph(attributePaths = "author")
  Optional<Post> findByIdAndDeletedAtIsNull(Long id);
}
