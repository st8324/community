package com.example.community.post;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

  /**
   * 목록 (POST-02). 작성자를 함께 읽어 항목마다 사용자 조회가 반복되지 않게 한다(N+1 방지). 정렬은 {@code Pageable}로 받는다. 대상이
   * 다대일(작성자 1명)이라 페이지 쿼리에 그대로 조인돼도 행 수가 늘지 않는다.
   */
  @EntityGraph(attributePaths = "author")
  Page<Post> findByDeletedAtIsNull(Pageable pageable);

  long countByDeletedAtIsNull();
}
