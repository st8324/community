package com.example.community.support;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/**
 * 게시글 테스트 데이터 도우미 (03 문서 HD-27). {@link IntegrationTest}가 등록한다.
 *
 * <p>글은 작성 API가 아니라 DB에 직접 넣는다 — 작성 API에 결함이 있어도 조회·수정·삭제 테스트의 결과가 오염되지 않게 하기 위해서다. 엔티티 클래스에도 의존하지
 * 않으므로 구현 전에도 컴파일된다. 모든 SQL은 {@code ?} 바인딩만 쓴다 (SEC-09).
 */
public class PostFixtures {

  private final JdbcTemplate jdbc;
  private final Clock clock;

  public PostFixtures(JdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  /** 작성 시각 = 지금. 제목·본문은 고유한 값. */
  public long create(long authorId) {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    return create(authorId, "제목 " + suffix, "본문 " + suffix, LocalDateTime.now(clock));
  }

  public long create(long authorId, String title, String content, LocalDateTime createdAt) {
    KeyHolder keys = new GeneratedKeyHolder();
    jdbc.update(
        con -> {
          PreparedStatement ps =
              con.prepareStatement(
                  "INSERT INTO posts (author_id, title, content, created_at) VALUES (?, ?, ?, ?)",
                  Statement.RETURN_GENERATED_KEYS);
          ps.setLong(1, authorId);
          ps.setString(2, title);
          ps.setString(3, content);
          ps.setObject(4, createdAt);
          return ps;
        },
        keys);
    return keys.getKey().longValue();
  }

  /** 소프트 삭제된 상태로 만든다 (DATA-03). */
  public void softDelete(long postId, long deletedBy) {
    jdbc.update(
        "UPDATE posts SET deleted_at = ?, deleted_by = ? WHERE id = ?",
        LocalDateTime.now(clock),
        deletedBy,
        postId);
  }

  /** 저장된 행 그대로 (저장값·소프트 삭제 기록 확인용). 컬럼 이름은 소문자. */
  public Map<String, Object> row(long postId) {
    return jdbc.queryForMap("SELECT * FROM posts WHERE id = ?", postId);
  }

  /**
   * 지금까지 저장된 글 중 가장 늦은 작성 시각. 목록 테스트는 이보다 뒤의 시각으로 글을 만들어, 다른 테스트가 남긴 글과 섞이지 않고 첫 페이지 맨 위에 오게 한다
   * (테스트는 DB를 비우지 않는다, 03 문서 5.1절).
   */
  public LocalDateTime latestCreatedAt() {
    LocalDateTime latest =
        jdbc.queryForObject("SELECT MAX(created_at) FROM posts", LocalDateTime.class);
    return latest == null ? LocalDateTime.now(clock) : latest;
  }
}
