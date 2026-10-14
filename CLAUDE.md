# CLAUDE.md — AI 작업 규칙

이 저장소는 **보안·하네스 엔지니어링·자동배포 학습용** 커뮤니티 사이트다. 기능보다 "작게 만들고, 자동으로 검증하고, 안전하게 배포하는 과정"이 목적이다.
AI(Claude 등)는 코드를 쓰기 전에 이 파일을 끝까지 읽고 따른다. 이 파일과 문서가 충돌하면 **문서(docs/)가 우선**이고, 충돌을 사용자에게 알린다.

## 1. 먼저 읽을 것

| 문서 | 내용 |
|---|---|
| `docs/01_요구사항_MVP.md` | 범위, 권한 매트릭스(3.1), AC, 보안 요구사항(SEC-xx) |
| `docs/02_기술스택_아키텍처.md` | 스택, API 목록(5.3), 계층 책임(5.2), 보안 대응표(8장) |
| `docs/03_하네스.md` | 테스트 구조, CI, 완료의 정의(DoD) |
| `backend/src/test/resources/authz/permission-matrix.csv` | 권한 테스트의 단일 기준 |
| `backend/src/test/resources/security/security-regression.csv` | 보안 회귀 테스트 목록 |

## 2. 절대 규칙

1. **범위**: 01 문서 2.1절 기능만 만든다 (회원가입·로그인·로그아웃, 게시글 CRUD, 댓글 작성·조회·삭제, 관리자 삭제). 범위 밖 기능은 사용자가 명시적으로 요청할 때만, 그리고 문서를 먼저 고친 뒤에 만든다. 먼저 제안하지 않는다.
2. **문서 먼저**: 동작·범위·보안 규칙·API·스키마가 바뀌는 결정이면 코드보다 **문서를 먼저** 갱신하고(버전·변경 이력·결정 기록 포함) 사용자 확인을 받는다.
3. **테스트 먼저**: 구현 전에 AC 테스트를 작성해 사용자에게 보여 주고, 실패하는 것을 확인한 뒤 구현한다.
4. **하네스를 약하게 만들지 않는다**. 다음은 금지이며, 필요해 보이면 멈추고 사용자에게 이유와 함께 묻는다.
   - 테스트 비활성화: `@Disabled`, `@Ignore`, `it.skip`, `.only` (빌드·린트가 막는다)
   - 실패하는 테스트의 기대값을 구현에 맞게 바꾸기 (요구사항이 바뀐 경우에만, 문서 먼저)
   - `eslint-disable` 주석 (설정에서 막혀 있다), 린트·Spotless 규칙 완화
   - `.trivyignore.yaml`·`.gitleaks.toml` 예외 추가, CI 잡·필수 체크 삭제, `--legacy-peer-deps`/`--force` 설치
   - 권한 매트릭스·보안 회귀 CSV에서 행 삭제, 또는 검증하지 않는 행을 `ACTIVE`로 표시
5. **시크릿**: 키·비밀번호·토큰을 코드·테스트·문서·커밋 메시지에 쓰지 않는다. 테스트용 JWT 키도 실행 시 무작위 생성한다. `.env`는 커밋하지 않는다.
6. **의존성**: 새 라이브러리는 이유를 설명하고 사용자 동의를 받은 뒤 추가한다. 추가하면 잠금 파일(`backend/gradle.lockfile`, `frontend/package-lock.json`)을 같은 커밋에 갱신한다.
7. **CI 보안**: `pull_request_target` 금지, 액션은 커밋 SHA 고정, 워크플로 기본 권한 읽기 전용.
8. **트러블슈팅은 원인 확인 먼저**: 빌드·검사가 실패하면 추측으로 설정을 바꾸지 않는다. `--stacktrace`, `build/` 아래 상세 기록(예: `build/spotless-lints/`, `build/reports/`)에서 `Caused by`의 마지막 원인을 찾아 사용자에게 보여 준 뒤 고친다. 사용자 환경에서 실행하지 못한 것은 "검증 안 됨"이라고 명시한다.
9. **도구 버전**: 플러그인이 관리하는 하위 도구의 버전(예: Spotless의 google-java-format)은 직접 고정하지 않는다 (궁합 문제, 03 문서 11.3절). Gradle 데몬 JVM은 `gradle-daemon-jvm.properties`로 21 고정을 유지한다.

## 3. 코드 작성 규칙 (보안 체크리스트)

모든 코드는 제시하기 전에 아래로 **자체 점검**하고, 점검 결과를 짧게 함께 적는다.

| 영역 | 규칙 | 근거 |
|---|---|---|
| 입력 | 서버에서 Bean Validation, trim 후 길이 검사 | SEC-01 |
| 인가 | 수정·삭제는 **Service 계층**에서 `author_id` 또는 ADMIN 확인. Controller·프론트의 확인은 보조 | SEC-07 |
| 응답 | 엔티티를 직접 반환하지 않고 DTO 사용. password_hash·role·이메일 원문·deleted_* 금지. 권한은 `canEdit`/`canDelete`로만 | DATA-05 |
| DB | Spring Data JPA 파라미터 바인딩. 문자열 연결 쿼리·네이티브 쿼리 문자열 조립 금지. 스키마 변경은 Flyway SQL만 | SEC-09 |
| 오류 | `@RestControllerAdvice` 표준 형식. 스택트레이스·SQL·내부 경로 금지 | SEC-10 |
| 로그 | 요청 본문·쿠키·Authorization 헤더·비밀번호·토큰 로깅 금지 | SEC-11 |
| 보안 기능 | 직접 구현하지 않는다. JWT·BCrypt·CSRF·보안 헤더는 Spring Security 기능을 설정해서 쓴다 | 02 문서 1장 |
| 프론트 | 사용자 입력은 JSX 텍스트로만 렌더링. `dangerouslySetInnerHTML`·`innerHTML` 금지. `fetch`는 `src/api/`에서만. 브라우저 저장소 금지 | SEC-05, SEC-08 |

## 4. 테스트 작성 규칙

- API 통합 테스트는 `@IntegrationTest`(실제 MySQL 8.4 + MockMvc)를 붙인다. H2·모킹 DB 금지.
- 테스트 이름은 한글로 AC를 그대로 읽히게 쓴다. 예: `타인_글을_수정하면_403이다()`.
- 기능을 구현하면 해당 **권한 매트릭스 행**과 **보안 회귀 항목**을 `PENDING → ACTIVE`로 바꾸고, 실제로 검증하는 테스트(`PermissionMatrixTest`, `security/*SecurityTest`)를 작성한다. 명세 검사 테스트가 연결을 확인한다.
- 상태 변경 요청 테스트에는 항상 CSRF 토큰을 넣는다 (`with(csrf())`). CSRF 누락 자체는 보안 회귀 S-06에서만 검증한다.
- 시각은 `Clock`을 주입해 고정한다 (만료 토큰 테스트).

## 5. 명령어

```bash
./scripts/verify.sh                 # 전체 (CI와 같은 검사, 이미지 스캔 제외). PR 전에 반드시 실행
./scripts/verify.sh backend         # 백엔드만
./scripts/verify.sh frontend        # 프론트만
./scripts/verify.sh scan            # Gitleaks + Trivy fs

cd backend
./gradlew check                     # Spotless + 테스트 비활성화 검사 + JUnit(Testcontainers)
./gradlew spotlessApply             # Java 포맷 자동 수정
./gradlew test --tests '*PermissionMatrix*'
./gradlew check -Pharness.strict=true   # PENDING 항목이 남아 있으면 실패
./gradlew resolveAndLockAll --write-locks   # 의존성 변경 후 잠금 파일 갱신

cd frontend
npm ci --ignore-scripts             # 설치 (install 스크립트 실행 안 함)
npm run verify                      # lint + format:check + test + build
npm run format                      # Prettier 자동 수정
```

전제: Docker 실행 중, JDK 21, Node 22. Gradle 설치는 필요 없다 (Wrapper 사용).
Windows: Git Bash 권장. cmd에서는 `./gradlew` 대신 `gradlew`, PowerShell은 `.\gradlew`. `verify.sh`는 Git Bash에서만 실행된다.

## 6. 작업 순서 (기능 1개 기준)

1. 관련 요구사항 ID와 AC, 권한 매트릭스 행을 확인한다. 문서 변경이 필요하면 여기서 멈추고 문서부터.
2. 실패하는 테스트를 작성해 사용자에게 보여 준다 (AC 테스트 + 매트릭스 행 ACTIVE + 보안 회귀 항목).
3. 최소한의 구현으로 테스트를 통과시킨다.
4. 보안 체크리스트(3장)로 자체 점검하고 결과를 적는다.
5. `./scripts/verify.sh` 통과를 확인한다.
6. PR 템플릿의 DoD를 채운다. 결정한 사항은 마지막에 요약한다.

## 7. 하지 말 것 요약

범위 밖 기능 제안 · 문서 없이 동작 변경 · 테스트 끄기 · 기대값 낮추기 · 린트/스캔 예외 추가 · 시크릿 하드코딩 · 무단 의존성 추가 · 보안 기능 직접 구현 · `pull_request_target` · 액션 태그 참조
