package com.example.community.comment;

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
 * DATA-04 댓글. 컨트롤러 응답으로 직접 반환하지 않는다 — {@link CommentResponse}로 바꾼다 (DATA-05).
 *
 * <p>시각은 모두 UTC {@link LocalDateTime}. {@code toString}은 정의하지 않는다 (작성자 엔티티를 거쳐 이메일이 로그에 섞이는 통로를 만들지
 * 않음, SEC-11).
 */
@Entity
@Table(name = "comments")
public class Comment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 글 관계 대신 id만 가진다 — 글의 존재·삭제 여부는 서비스가 PostRepository로 직접 확인한다 (01 D-31). */
  @Column(name = "post_id", nullable = false, updatable = false)
  private Long postId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "author_id", nullable = false, updatable = false)
  private User author;

  @Column(nullable = false, length = 1000)
  private String content;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  /** 삭제한 사용자 id (ADM-03). 관계로 매핑하지 않는다 — 읽을 일이 없고 응답에도 나가지 않는다. */
  @Column(name = "deleted_by")
  private Long deletedBy;

  protected Comment() {}

  private Comment(Long postId, User author, String content, LocalDateTime createdAt) {
    this.postId = postId;
    this.author = author;
    this.content = content;
    this.createdAt = createdAt;
  }

  /**
   * @param content 검증을 마친(strip된) 내용
   */
  static Comment create(long postId, User author, String content, LocalDateTime now) {
    return new Comment(postId, author, content, now);
  }

  /** CMT-03 ①, DATA-03. 행은 지우지 않고 삭제 시각과 삭제자만 기록한다. 권한 확인은 CommentService가 먼저 한다. */
  void softDelete(long deletedBy, LocalDateTime now) {
    this.deletedAt = now;
    this.deletedBy = deletedBy;
  }

  boolean isWrittenBy(long userId) {
    return author.getId() == userId;
  }

  public Long getId() {
    return id;
  }

  public Long getPostId() {
    return postId;
  }

  public User getAuthor() {
    return author;
  }

  public String getContent() {
    return content;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
