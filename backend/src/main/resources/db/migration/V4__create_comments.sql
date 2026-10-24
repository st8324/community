-- DATA-04 Comment (01 문서 v0.6, 02 문서 v1.7 5.6절).
-- content:    VARCHAR(1000). utf8mb4에서 길이는 "문자 수" → 앱의 코드 포인트 검사(01 D-28)와 한도가 같다.
-- updated_at: 두지 않는다 (댓글 수정 기능 없음, 01 7장).
-- deleted_at / deleted_by: 소프트 삭제 기록 (DATA-03, ADM-03). 행은 지우지 않는다.
-- FK는 삭제 연쇄(ON DELETE CASCADE)를 두지 않는다. 글이 소프트 삭제돼도 댓글 행은 그대로 두고,
-- 조회·삭제 시점에 글 상태를 확인한다 (01 D-31).
-- 인덱스: 목록 조회 "post_id = ? AND deleted_at IS NULL ORDER BY created_at ASC, id ASC" (CMT-02 ②③)
CREATE TABLE comments (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    post_id    BIGINT        NOT NULL,
    author_id  BIGINT        NOT NULL,
    content    VARCHAR(1000) NOT NULL,
    created_at DATETIME(6)   NOT NULL,
    deleted_at DATETIME(6)   NULL,
    deleted_by BIGINT        NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_comments_post FOREIGN KEY (post_id) REFERENCES posts (id),
    CONSTRAINT fk_comments_author FOREIGN KEY (author_id) REFERENCES users (id),
    CONSTRAINT fk_comments_deleted_by FOREIGN KEY (deleted_by) REFERENCES users (id),
    INDEX ix_comments_list (post_id, deleted_at, created_at, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
