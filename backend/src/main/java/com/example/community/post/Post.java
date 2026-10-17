package com.example.community.post;

import com.example.community.auth.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * DATA-02 게시글. 컨트롤러 응답으로 직접 반환하지 않는다 — {@link PostResponse}로 바꾼다 (DATA-05).
 *
 * <p>시각은 모두 UTC {@link LocalDateTime} (hibernate.jdbc.time_zone=UTC, Clock은 UTC). {@code toString}은
 * 정의하지 않는다 (작성자 엔티티를 거쳐 이메일이 로그에 섞이는 통로를 만들지 않음, SEC-11).
 */
@Entity
@Table(name = "posts")
public class Post {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "author_id", nullable = false, updatable = false)
  private User author;

  @Column(nullable = false, length = 100)
  private String title;

  // String 기본 매핑은 VARCHAR → 스키마 검증(validate)이 TEXT 컬럼과 불일치로 볼 수 있어 컬럼 정의를 명시한다 (02 문서 5.6절).
  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  /** 수정 전에는 null (01 D-23). */
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  /** 삭제한 사용자 id (ADM-03). 관계로 매핑하지 않는다 — 읽을 일이 없고 응답에도 나가지 않는다. */
  @Column(name = "deleted_by")
  private Long deletedBy;

  protected Post() {}

  private Post(User author, String title, String content, LocalDateTime createdAt) {
    this.author = author;
    this.title = title;
    this.content = content;
    this.createdAt = createdAt;
  }

  /**
   * @param title 검증을 마친(strip된) 제목
   * @param content 검증을 마친(strip된) 본문
   */
  public static Post create(User author, String title, String content, LocalDateTime now) {
    return new Post(author, title, content, now);
  }

  /** POST-04. 권한 확인은 호출하는 쪽(PostService)이 먼저 한다. */
  void update(String title, String content, LocalDateTime now) {
    this.title = title;
    this.content = content;
    this.updatedAt = now;
  }

  /** POST-05 ①, DATA-03. 행은 지우지 않고 삭제 시각과 삭제자만 기록한다. 권한 확인은 PostService가 먼저 한다. */
  void softDelete(long deletedBy, LocalDateTime now) {
    this.deletedAt = now;
    this.deletedBy = deletedBy;
  }

  public boolean isWrittenBy(long userId) {
    return author.getId() == userId;
  }

  public Long getId() {
    return id;
  }

  public User getAuthor() {
    return author;
  }

  public String getTitle() {
    return title;
  }

  public String getContent() {
    return content;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }
}
