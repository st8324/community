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
 * 댓글 테스트 데이터 도우미 ({@link PostFixtures}와 같은 이유로 DB에 직접 넣는다, 03 문서 HD-27). 엔티티 클래스에 의존하지 않으므로 구현 전에도
 * 컴파일된다. 모든 SQL은 {@code ?} 바인딩만 쓴다 (SEC-09).
 */
public class CommentFixtures {

  private final JdbcTemplate jdbc;
  private final Clock clock;

  public CommentFixtures(JdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  /** 작성 시각 = 지금, 내용은 고유한 값. */
  public long create(long postId, long authorId) {
    return create(
        postId,
        authorId,
        "댓글 " + UUID.randomUUID().toString().substring(0, 8),
        LocalDateTime.now(clock));
  }

  public long create(long postId, long authorId, String content, LocalDateTime createdAt) {
    KeyHolder keys = new GeneratedKeyHolder();
    jdbc.update(
        con -> {
          PreparedStatement ps =
              con.prepareStatement(
                  "INSERT INTO comments (post_id, author_id, content, created_at)"
                      + " VALUES (?, ?, ?, ?)",
                  Statement.RETURN_GENERATED_KEYS);
          ps.setLong(1, postId);
          ps.setLong(2, authorId);
          ps.setString(3, content);
          ps.setObject(4, createdAt);
          return ps;
        },
        keys);
    return keys.getKey().longValue();
  }

  /** 소프트 삭제된 상태로 만든다 (DATA-03). */
  public void softDelete(long commentId, long deletedBy) {
    jdbc.update(
        "UPDATE comments SET deleted_at = ?, deleted_by = ? WHERE id = ?",
        LocalDateTime.now(clock),
        deletedBy,
        commentId);
  }

  /** 저장된 행 그대로. 컬럼 이름은 소문자. */
  public Map<String, Object> row(long commentId) {
    return jdbc.queryForMap("SELECT * FROM comments WHERE id = ?", commentId);
  }
}
