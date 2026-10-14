# syntax=docker/dockerfile:1
# 멀티스테이지: 프론트 빌드 → 백엔드 빌드(프론트 산출물 포함) → JRE 런타임(non-root)
# 베이스 이미지 태그는 Dependabot(docker)이 갱신 PR을 만든다. 운영 전 digest 고정 예정 (03 문서 H-Q6).

FROM node:22-bookworm-slim AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --ignore-scripts
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:21-jdk AS backend
WORKDIR /app/backend
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts backend/gradle.lockfile ./
COPY backend/gradle ./gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY backend/src ./src
COPY --from=frontend /app/frontend/dist ./src/main/resources/static
# 테스트는 CI의 backend 잡에서 실행했으므로 이미지 빌드에서는 생략
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre AS runtime
# 베이스 이미지에 들어 있는 pebble(Ubuntu 서비스 관리자, Go 바이너리)은 이 앱에서 쓰지 않는다 (ENTRYPOINT는 java).
# dpkg 패키지가 아니라 파일로만 들어 있어 직접 삭제한다. 취약한 Go 런타임 포함: CVE-2026-78667, -78669, -97031 (03 문서 HD-16)
RUN rm -f /usr/bin/pebble
RUN groupadd --system app && useradd --system --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=backend /app/backend/build/libs/*.jar /app/app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
