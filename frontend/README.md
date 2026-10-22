# 프론트엔드

Next.js 16 + MapLibre GL JS 6. 전선 스냅샷을 백엔드 `GET /api/snapshots`에서 받아 지도에 그리고, 스냅샷 사이를 넘길 때 전선을 움직인다.

## 실행

준비: Node.js LTS, 백엔드 실행 중(`backend/`에서 `./gradlew bootRun` 또는 IntelliJ)

```bash
npm install
npm run dev        # http://localhost:3000
```

- `/api/*` 요청은 Next 서버가 백엔드(기본 `http://localhost:8080`)로 넘긴다. 백엔드 주소가 다르면 `BACKEND_URL` 환경 변수로 바꾼다.
- `npm run dev`와 `npm run build`는 시작 전에 maplibre-gl의 워커 파일을 `public/`으로 복사한다 (`scripts/copy-maplibre-worker.mjs`).

## 폰에서 보기

PC와 폰이 같은 Wi-Fi에 있으면 된다.

1. `npm run dev`를 실행하면 나오는 `- Network: http://192.168.x.x:3000` 주소를 폰 브라우저에 입력한다.
2. 열리지 않으면 Windows 방화벽이 Node.js를 막고 있을 수 있다. 처음 실행할 때 뜨는 창에서 "개인 네트워크" 허용을 누르거나, Windows 보안 → 방화벽 → "앱 허용"에서 Node.js를 개인 네트워크에 허용한다.
3. 화면이 "불러오는 중"에서 멈추면 주소의 앞자리가 `next.config.ts`의 `allowedDevOrigins`(192.168 / 10 / 172로 시작하는 사설 IP)에 들어 있는지 확인한다.

## 구조

| 경로 | 내용 |
| --- | --- |
| `src/components/WarMapApp.tsx` | 화면 전체: 날짜, 지도, 범례, 타임라인, 이전/다음 |
| `src/components/FrontlineMap.tsx` | MapLibre 지도. 스냅샷이 바뀌면 전선을 1.2초 동안 옮긴다 |
| `src/components/Timeline.tsx` | 전쟁 기간 전체 줄과 스냅샷 점 |
| `src/lib/geometry.ts` | 전선으로 양측 나누기(반평면), 보간, 추가 영역의 쪽 판정 |
| `src/lib/map-style.ts` | 지도 레이어와 색 |
| `src/lib/snapshots.ts` | API 응답 타입, 날짜 계산 |
| `public/geo/` | 바다 덮개와 주변 나라. 만드는 방법은 [data/reference/README.md](../data/reference/README.md) |

## 지도 그리는 방식

배경 타일을 쓰지 않는다. 현대 지도 타일에는 지금의 군사분계선과 지명이 나와서 1950년대 전선과 섞이기 때문이다.

아래에서 위로 이렇게 쌓는다.

1. 바다색 배경
2. 북측 빨강, 남측 파랑: 전선 양 끝을 정서·정동으로 늘려 틀을 둘로 나눈 다각형 (기획서 6장의 반평면 방식)
3. 추가 영역(고립 지역·교두보): 둘러싼 쪽의 반대 색
4. 바다 덮개: 틀에서 한반도를 뺀 모양. 2~3이 육지 밖으로 보이지 않게 가린다
5. 주변 나라(회색), 해안선, 전선

모든 전선은 API에서 점 500개로 오므로, 스냅샷을 넘길 때 i번째 점끼리 옮기면 된다. 화면 움직임 줄이기 설정을 켠 기기에서는 옮기지 않고 바로 바꾼다.
