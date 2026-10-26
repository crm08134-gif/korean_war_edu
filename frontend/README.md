# 프론트엔드

Next.js 16 + MapLibre GL JS 6. 전선 스냅샷을 백엔드 `GET /api/snapshots`에서 받아 지도에 그리고, 스냅샷 사이를 넘길 때 전선을 움직인다. 사건은 `GET /api/events`에서 받아 그 시기의 마커와 칩으로 보여 주고, 누르면 하단 패널이 사건 카드로 바뀐다.

## 실행

준비: Node.js LTS, 백엔드 실행 중(`backend/`에서 `./gradlew bootRun` 또는 IntelliJ)

```bash
npm install
npm run dev        # http://localhost:3000
```

- `/api/*`와 사건 카드 사진(`/photos/*`) 요청은 Next 서버가 백엔드(기본 `http://localhost:8080`)로 넘긴다. 백엔드 주소가 다르면 `BACKEND_URL` 환경 변수로 바꾼다.
- 백엔드는 검수된(`reviewed`) 사건만 내보낸다. 검수 전(`draft`) 사건까지 보려면 백엔드를 환경 변수 `EVENTS_INCLUDE_DRAFTS=true`로 켠다. 화면에는 검수 여부를 표시하지 않는다.
- `npm run dev`와 `npm run build`는 시작 전에 maplibre-gl의 워커 파일을 `public/`으로 복사한다 (`scripts/copy-maplibre-worker.mjs`).

## 폰에서 보기

PC와 폰이 같은 Wi-Fi에 있으면 된다.

1. `npm run dev`를 실행하면 나오는 `- Network: http://192.168.x.x:3000` 주소를 폰 브라우저에 입력한다.
2. 열리지 않으면 Windows 방화벽이 Node.js를 막고 있을 수 있다. 처음 실행할 때 뜨는 창에서 "개인 네트워크" 허용을 누르거나, Windows 보안 → 방화벽 → "앱 허용"에서 Node.js를 개인 네트워크에 허용한다.
3. 화면이 "불러오는 중"에서 멈추면 주소의 앞자리가 `next.config.ts`의 `allowedDevOrigins`(192.168 / 10 / 172로 시작하는 사설 IP)에 들어 있는지 확인한다.

### 다른 네트워크(LTE, 집 Wi-Fi)에서 보기

PC의 3000번을 HTTPS 주소로 잠깐 열어 준다. 고정 주소 배포 전까지 테스트용이다.

```bash
npm run build
npm run start                                   # 백엔드도 켜 둔다
cloudflared tunnel --url http://localhost:3000  # 다른 터미널. https://….trycloudflare.com 주소가 나온다
```

- cloudflared 설치(Windows): `winget install -e --id Cloudflare.cloudflared`
- 계정 없이 쓰는 Quick Tunnel이라 실행할 때마다 주소가 바뀐다. 터미널을 닫으면 주소도 닫힌다.

### 홈 화면에 설치 (PWA)

주소창 없이 앱처럼 열린다. HTTPS 주소(위 터널)에서 설치한다. `http://192.168.x.x` 주소에서는 바로가기만 생기고 앱처럼 열리지 않는다.

- 안드로이드(크롬): ⋮ → 앱 설치(또는 홈 화면에 추가 → 설치)
- 아이폰(사파리): 공유 → 홈 화면에 추가

설치 정보는 `src/app/manifest.ts`, 아이폰 설정은 `src/app/layout.tsx`의 `appleWebApp`에 있다.

### 앱 아이콘

전투복 무늬 색으로 칠한 남한을 배경으로, 거꾸로 세운 총 위에 태극기를 그린 철모를 얹었다. 전사자를 기리는 모습이다.

- 태극기: 국가기록원의 국기 작도법과 국기법 시행령의 색으로 그린다.
- 남한: `data/reference/south_korea_outline.geojson`에서 그리고, 본토가 아이콘 높이를 채운다. 제주·울릉도·독도는 틀 밖이라 잘린다.
- 무늬: 육군 전투복(화강암 무늬)의 다섯 색에 맞춘 근사색이다. 무늬 모양은 군복 도안을 옮기지 않고 스크립트가 고정 시드로 새로 만든다.

그림을 바꾸면 `scripts/make-app-icons.mjs`를 고친 뒤 `npm run icons`로 PNG 다섯 개를 다시 만들어 함께 커밋한다.

## 구조

| 경로 | 내용 |
| --- | --- |
| `src/components/WarMapApp.tsx` | 화면 전체: 날짜와 사건 이름, 지도, 범례, 사건 칩, 타임라인, 이전/다음. 사건을 고르면 하단 패널이 카드로 바뀐다 |
| `src/components/FrontlineMap.tsx` | MapLibre 지도. 스냅샷이 바뀌면 전선을 1.2초 동안 옮긴다. 사건 마커는 HTML 마커로 맨 위에 놓는다 |
| `src/components/EventCard.tsx` | 사건 카드: 날짜·장소, 제목, 대표 사진, 요약, 본문(소제목·문단·표·사진), 출처 목록. 사진 아래에 출처와 라이선스 |
| `src/components/KindIcon.tsx` | 사건 종류 아이콘 (칩, 카드) |
| `src/components/Timeline.tsx` | 전쟁 기간 전체 줄과 스냅샷 점 |
| `src/lib/geometry.ts` | 전선으로 양측 나누기(반평면), 보간, 추가 영역의 쪽 판정 |
| `src/lib/map-style.ts` | 지도 레이어와 색 |
| `src/lib/snapshots.ts` | API 응답 타입, 날짜 계산 |
| `src/lib/event-kinds.ts` | 사건 종류 이름과 아이콘 모양(직접 그린 SVG path). 지도 마커도 같은 모양을 쓴다 |
| `src/lib/events.ts` | 사건 API 응답 타입, 본문 블록 타입, 스냅샷 구간의 사건 고르기 |
| `public/geo/` | 바다 덮개와 주변 나라. 만드는 방법은 [data/reference/README.md](../data/reference/README.md) |
| `src/app/manifest.ts`, `public/icons/`, `src/app/icon.png`, `src/app/apple-icon.png` | 홈 화면 설치(PWA) 정보와 아이콘 (`scripts/make-app-icons.mjs`로 만듦) |

## 지도 그리는 방식

배경 타일을 쓰지 않는다. 현대 지도 타일에는 지금의 군사분계선과 지명이 나와서 1950년대 전선과 섞이기 때문이다.

아래에서 위로 이렇게 쌓는다.

1. 바다색 배경
2. 북측 빨강, 남측 파랑: 전선 양 끝을 정서·정동으로 늘려 틀을 둘로 나눈 다각형 (기획서 6장의 반평면 방식)
3. 추가 영역(고립 지역·교두보): 둘러싼 쪽의 반대 색
4. 바다 덮개: 틀에서 한반도를 뺀 모양. 2~3이 육지 밖으로 보이지 않게 가린다
5. 주변 나라(회색), 해안선, 전선
6. 사건 마커: 종류 아이콘(전투, 공세·돌파 등)을 담은 흰 원과 이름. 고른 사건은 검정 원에 흰 아이콘. 지도 위 HTML 요소라 늘 맨 위에 보인다

모든 전선은 API에서 점 500개로 오므로, 스냅샷을 넘길 때 i번째 점끼리 옮기면 된다. 화면 움직임 줄이기 설정을 켠 기기에서는 옮기지 않고 바로 바꾼다.
