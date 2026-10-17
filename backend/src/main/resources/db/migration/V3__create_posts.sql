-- DATA-02 Post, DATA-03 소프트 삭제 (01 문서 v0.5, 02 문서 v1.5 5.6절).
-- title:   VARCHAR(100). utf8mb4에서 길이는 "문자 수" → 앱의 코드 포인트 검사(01 D-22)와 한도가 같다.
-- content: TEXT(65,535바이트). 10,000자 × 최대 4바이트 = 40,000바이트라 충분하다.
-- updated_at: 수정 전에는 NULL (01 D-23 — 응답의 updatedAt=null로 "수정됨" 여부를 알 수 있다).
-- deleted_at / deleted_by: 소프트 삭제 기록. 행은 지우지 않는다. deleted_by는 관리자 삭제 추적(ADM-03)에 쓴다.
-- FK는 삭제 연쇄(ON DELETE CASCADE)를 두지 않는다: 사용자 행이 지워지면서 글 기록이 조용히 사라지면 안 된다.
-- 인덱스: 목록 조회 "deleted_at IS NULL ORDER BY created_at DESC, id DESC" (POST-02 ①③)
CREATE TABLE posts (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    author_id  BIGINT       NOT NULL,
    title      VARCHAR(100) NOT NULL,
    content    TEXT         NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NULL,
    deleted_at DATETIME(6)  NULL,
    deleted_by BIGINT       NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_posts_author FOREIGN KEY (author_id) REFERENCES users (id),
    CONSTRAINT fk_posts_deleted_by FOREIGN KEY (deleted_by) REFERENCES users (id),
    INDEX ix_posts_list (deleted_at, created_at, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
