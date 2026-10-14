#!/usr/bin/env bash
# 로컬에서 CI(ci.yml)와 같은 검사를 한 번에 실행한다. 전제: Docker, JDK 21, Node 22.
# 사용법: ./scripts/verify.sh            전체
#         ./scripts/verify.sh backend    백엔드만
#         ./scripts/verify.sh frontend   프론트만
#         ./scripts/verify.sh scan       보안 스캔만 (Gitleaks · Trivy fs)
# 이미지 스캔(Trivy image)은 시간이 오래 걸려 CI에서만 실행한다.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TARGET="${1:-all}"
GITLEAKS_IMAGE="ghcr.io/gitleaks/gitleaks:v8.30.1"
TRIVY_IMAGE="aquasec/trivy:0.75.0"

step() { printf '\n\033[1;34m==> %s\033[0m\n' "$1"; }

backend() {
  step "backend: ./gradlew check (Spotless · 테스트 비활성화 금지 · JUnit · Testcontainers)"
  (cd "$ROOT/backend" && ./gradlew check)
}

frontend() {
  step "frontend: npm ci → lint → format:check → test → build"
  (cd "$ROOT/frontend" && npm ci --ignore-scripts && npm run verify)
}

scan() {
  step "secret scan: Gitleaks (전체 git 이력)"
  docker run --rm -v "$ROOT:/repo" -w /repo "$GITLEAKS_IMAGE" \
    git /repo --config /repo/.gitleaks.toml --redact --exit-code 1

  step "dependency scan: Trivy fs (CRITICAL/HIGH, 수정 버전 있는 것만)"
  docker run --rm -v "$ROOT:/repo" -w /repo "$TRIVY_IMAGE" \
    fs --scanners vuln --severity CRITICAL,HIGH --ignore-unfixed \
    --ignorefile .trivyignore.yaml --exit-code 1 .
}

case "$TARGET" in
  backend) backend ;;
  frontend) frontend ;;
  scan) scan ;;
  all) backend; frontend; scan ;;
  *) echo "알 수 없는 대상: $TARGET (backend|frontend|scan|all)"; exit 2 ;;
esac

step "통과 ✅ — PR을 올려도 좋습니다 (이미지 스캔은 CI에서 확인)"
