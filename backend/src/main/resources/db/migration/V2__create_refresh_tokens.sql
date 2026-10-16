-- DATA-06 RefreshToken (01 문서 v0.4, 02 문서 5.6절). 로그인 상태 유지(AUTH-06).
-- token_hash: 원문이 아니라 SHA-256 hex(64자)만 저장 (SEC-16). ascii_bin → 정확 비교, 유일 인덱스로 바로 조회.
-- family_id: 로그인 1회 = 묶음 1개(UUID). 재사용 감지 시 묶음 전체를 한 번에 폐기한다.
-- revoked_at: 교체·로그아웃·재사용 감지로 폐기된 시각. 행을 지우지 않아야 "이미 쓴 토큰"을 알아볼 수 있다.
-- 문자열 컬럼은 VARCHAR: CHAR로 두면 Hibernate 스키마 검증(validate)이 String 필드와 타입 불일치로 판단할 수 있다.
CREATE TABLE refresh_tokens (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    family_id  VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    token_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX ix_refresh_tokens_family (family_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
