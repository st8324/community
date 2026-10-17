-- DATA-01 User. 02 문서 5.6절.
-- email: VARCHAR(255)는 02 문서 5.6절 그대로, 입력 상한(254자)은 앱에서 검사한다(AUTH-01 ⑥).
--        앱에서 trim + 소문자 정규화 후 저장(AUTH-01 ⑤). 콜레이션을 utf8mb4_bin으로 고정해 DB는 "정확히 같은 문자열"만 중복으로 본다.
--        (기본 콜레이션 utf8mb4_0900_ai_ci는 악센트·대소문자를 무시해 서로 다른 이메일을 중복으로 판단할 수 있다)
-- password_hash: DelegatingPasswordEncoder 형식 "{bcrypt}$2a$10$..." (68자) — 알고리즘 교체 여유를 두고 100자.
-- role: VARCHAR + CHECK. MySQL ENUM은 값 추가 시 테이블 변경이 필요하고, JPA가 순서값으로 저장하는 실수를 가리기 쉽다.
CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(10)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
