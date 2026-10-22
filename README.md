# 6·25 전쟁 지도 (가칭)

6·25 전쟁 3년 1개월(1950-06-25 ~ 1953-07-27)을 지도 위에서 넘겨 보며 전선의 이동과 주요 사건을 확인하는 웹 앱입니다.

> 개발 중 · 1단계(전선과 사건)

## 저장소 구조

| 경로 | 내용 |
| --- | --- |
| `backend/` | Spring Boot API |
| `frontend/` | Next.js + MapLibre GL JS 지도 화면 |
| `data/frontlines/` | 전환점 전선 스냅샷(목록 CSV, GeoJSON)과 제작 규칙 |
| `data/reference/` | 한반도 외곽선 등 계산 기준 데이터 |
| `docs/adr/` | 설계 결정 기록 |
| `scripts/` | 저장소 설정 스크립트, 전선 스냅샷 제작 도구(`scripts/frontline/`) |

## 로컬 실행

준비: JDK 25, Docker Desktop, Node.js LTS

**백엔드**

```bash
cp .env.example .env    # POSTGRES_PASSWORD를 바꾼다. 5432를 이미 쓰고 있으면 POSTGRES_PORT도 바꾼다
cd backend
./gradlew bootRun       # PostGIS 컨테이너가 함께 뜬다 (Spring Boot Docker Compose 지원)
```

- 상태 확인: http://localhost:8080/actuator/health
- API 문서: http://localhost:8080/swagger-ui.html
- 전선 스냅샷: http://localhost:8080/api/snapshots (앱이 시작될 때 `data/frontlines/`의 파일로 DB를 맞춘다)
- IntelliJ에서 실행할 때는 Run Configuration의 Working directory를 `backend`로 지정한다

**프론트** (백엔드를 켜 둔 채로, 다른 터미널에서)

```bash
cd frontend
npm install
npm run dev             # http://localhost:3000
```

- 같은 Wi-Fi의 폰에서는 `npm run dev`가 알려 주는 `Network:` 주소(`http://192.168.x.x:3000`)로 연다. 방화벽 등은 [frontend/README.md](frontend/README.md) 참고

## 기술 스택

| 영역 | 선택 |
| --- | --- |
| 백엔드 | Java, Spring Boot, Spring AI |
| DB | PostgreSQL + PostGIS |
| 프론트 | Next.js, Tailwind, shadcn/ui, MapLibre GL JS |
| 전선 데이터 | QGIS → GeoJSON, Git으로 관리 |

## 개발 방식

- `feat/frontline-api` 형식 브랜치 → PR → Squash merge
- 커밋 메시지는 [Conventional Commits](https://www.conventionalcommits.org/ko/v1.0.0/) (`feat:`, `fix:`, `docs:` …)
- `main`에는 직접 푸시하지 않습니다
- 전선 데이터 변경도 PR로 올리고 근거 지도와 변경 이유를 남깁니다

## 데이터 출처와 라이선스

- 전선: 미 육군 군사사 센터(CMH) 공간사의 퍼블릭 도메인 지도를 지오레퍼런싱·트레이싱해 직접 제작합니다. 자세한 규칙은 [data/frontlines/README.md](data/frontlines/README.md)에 있습니다.
- 한반도 외곽선: [Natural Earth](https://www.naturalearthdata.com/) 1:10m v5.1.2 (퍼블릭 도메인). [data/reference/README.md](data/reference/README.md) 참고.
- 코드와 데이터의 라이선스는 따로 정할 예정입니다.
