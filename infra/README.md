# infra/

배포 파이프라인 설계 단계(04 문서)에서 채운다. 하네스 단계에서는 비워 둔다.

| 파일 (예정) | 용도 |
|---|---|
| `compose.prod.yml` | caddy · app · mysql |
| `Caddyfile` | IP 주소 인증서(shortlived) |
| `deploy.sh` | EC2에서 실행되는 배포 스크립트 |
| `ssm-deploy-document.json` | SSM 전용 배포 문서 |

이 폴더도 CI의 Gitleaks 검사 대상이다. 시크릿 값은 절대 두지 않는다.

