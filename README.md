# 6·25 전쟁 지도 (가칭)

6·25 전쟁 3년 1개월(1950-06-25 ~ 1953-07-27)을 지도 위에서 넘겨 보며 전선의 이동과 주요 사건을 확인하는 웹 앱입니다.

> 개발 중 · 1단계(전선과 사건)

## 저장소 구조

| 경로 | 내용 |
| --- | --- |
| `backend/` | Spring Boot API (예정) |
| `frontend/` | Next.js + MapLibre GL JS (예정) |
| `data/frontlines/` | 전환점 전선 스냅샷 GeoJSON과 제작 규칙 |
| `docs/adr/` | 설계 결정 기록 |
| `scripts/` | 저장소 설정 스크립트 |

## 로컬 실행

```bash
cp .env.example .env          # 비밀번호를 바꾼다
docker compose up -d          # PostgreSQL 18 + PostGIS 3.6 (localhost:5432)
```

백엔드와 프론트 실행 방법은 각 프로젝트를 만든 뒤 추가합니다.

## 기술 스택

| 영역 | 선택 |
| --- | --- |
| 백엔드 | Java, Spring Boot, Spring AI |
| DB | PostgreSQL + PostGIS |
| 프론트 | Next.js, Tailwind, shadcn/ui, MapLibre GL JS |
| 전선 데이터 | QGIS → GeoJSON, Git으로 관리 |

## 개발 방식

- 이슈 → `feat/#12-frontline-api` 형식 브랜치 → PR → Squash merge
- 커밋 메시지는 [Conventional Commits](https://www.conventionalcommits.org/ko/v1.0.0/) (`feat:`, `fix:`, `docs:` …)
- `main`에는 직접 푸시하지 않습니다
- 전선 데이터 변경도 PR로 올리고 근거 지도와 변경 이유를 남깁니다

## 데이터 출처와 라이선스

- 전선: 미 육군 군사사 센터(CMH) 공간사의 퍼블릭 도메인 지도를 지오레퍼런싱·트레이싱해 직접 제작합니다. 자세한 규칙은 [data/frontlines/README.md](data/frontlines/README.md)에 있습니다.
- 코드와 데이터의 라이선스는 따로 정할 예정입니다.
