# 전선 스냅샷 데이터

전선이 크게 바뀐 전환점마다 스냅샷을 하나씩 둔다. 좌표는 기억이나 추정으로 만들지 않고, 퍼블릭 도메인 원본 지도를 QGIS로 지오레퍼런싱해 트레이싱한다. 사료로 확인하지 않은 선은 쓰지 않는다. 정밀도는 폰 전국 화면에서 전선의 큰 흐름이 사료와 맞는 수준(약 1~2km)을 목표로 하고, 고지 단위 통제선까지는 그리지 않는다.

## 파일 구조

```
data/frontlines/
├─ snapshots.csv                    스냅샷 목록 (스냅샷당 한 줄)
├─ 1950-06-25.front_line.geojson    전선
└─ 1950-09-15.extra_areas.geojson   추가 영역 (있는 날짜만)
```

스냅샷 하나가 DB `frontline_snapshot`의 한 행이 된다. CSV의 열 이름과 도형 파일 이름(`front_line`, `extra_areas`)은 테이블 컬럼 이름과 같다.

### `snapshots.csv`

| 열 | 필수 | 내용 |
| --- | --- | --- |
| `snapshot_date` | 예 | `YYYY-MM-DD`. 도형 파일 이름의 날짜와 같아야 한다 |
| `source_map` | 예 | 근거 지도 (예: CMH *South to the Naktong* Map IV) |
| `note` | | 다른 지도·사료와의 차이 등 |

- UTF-8로 저장한다. IntelliJ 같은 텍스트 편집기로 고친다 (엑셀로 저장하면 한글 인코딩이 바뀔 수 있다).
- 쉼표가 들어간 값은 큰따옴표로 감싼다.

### 도형 파일

- **`<날짜>.front_line.geojson`:** Feature 하나, LineString 하나.
- **`<날짜>.extra_areas.geojson`:** 고립 지역·교두보마다 Polygon Feature 하나. 서로 겹치지 않게 그린다. DB에는 MultiPolygon 하나로 합쳐 넣는다.
- `properties`는 쓰지 않는다. 날짜·근거·메모는 `snapshots.csv`에만 적는다.

## 앱에 들어가는 방식

이 폴더가 기준이고 DB는 이 폴더를 따라간다. DB를 손으로 고쳐도 다음에 앱이 켜질 때 파일 내용으로 되돌아가므로, 고칠 때는 파일을 고쳐 PR로 올린다.

1. 빌드할 때 이 폴더의 `snapshots.csv`와 `*.geojson`이 백엔드 jar 안 `frontlines/`로 복사된다 (`backend/build.gradle`의 `processResources`).
2. 앱이 시작될 때 `FrontlineSnapshotLoader`가 DB `frontline_snapshot`을 맞춘다. CSV에 있는 날짜는 넣거나 덮어쓰고, CSV에 없는 날짜는 지운다.
3. 아래 중 하나라도 걸리면 이번 시작에서 바꾼 것을 모두 되돌리고(트랜잭션 하나) 앱이 뜨지 않는다. 로그에 어느 날짜·파일인지 나온다.
  - CSV: 모르는 열이 있다, 날짜나 근거 지도가 비었다, 같은 날짜가 두 번 있다, 줄이 하나도 없다
  - 파일: CSV 날짜의 `front_line` 파일이 없다, CSV에 없는 날짜의 도형 파일이 있다
  - 전선: Feature가 하나가 아니다, LineString이 아니다, 서→동이 아니다
  - 추가 영역: Polygon이 아니다, 서로 겹친다
4. PR마다 CI의 `./gradlew build`가 테스트용 PostGIS 컨테이너에 같은 방식으로 적재해 보므로, 위 규칙을 어긴 데이터는 머지 전에 걸린다.

끝점이 해안선 위에 있는지, 앞뒤 스냅샷과 견주어 이상하지 않은지는 앱이 보지 않는다. 제작 도구의 `validate`와 `preview`로 확인한다 ([scripts/frontline/README.md](../../scripts/frontline/README.md)).

## 규칙

- **전선:** 서해안에서 동해안까지 이어지는 선 하나. 방향은 서→동으로 통일한다.
- **끝점:** 선은 육지 위에서만 그리고, 양 끝점은 해안선(`data/reference/korea_outline.geojson`) 위에 둔다. 바다로 늘리지 않는다. 만(灣)처럼 육지 사이에 낀 바다는 선으로 이어도 된다.
- **추가 영역:** 전선 하나로 설명되지 않는 고립 지역·교두보(인천상륙 직후, 흥남 철수 등)는 따로 그린다.
- **양측 영역:** 저장하지 않는다. 외곽선을 전선으로 나눠 계산한다 (북측 빨강, 남측 파랑).
- **근거:** 그 날짜의 전선이 CMH 지도(필요하면 본문과 함께)로 확인될 때만 스냅샷을 만든다. 근거 지도는 `source_map`에 반드시 적는다.
- **좌표계:** GeoJSON 표준(RFC 7946)에 따라 WGS 84 경위도, 소수 6자리.
- **변경:** 모든 변경은 PR로 올리고, PR 설명에 근거 지도와 변경 이유를 적는다.

## 작업 순서

1. 브랜치 생성 (예: `data/1950-08-04-pusan-perimeter`)
2. 원본 지도를 받아 `data/raw/`에 둔다 (Git에 올리지 않음)
3. QGIS 지오레퍼런서로 기준점(도시·강 합류점 등)을 찍어 좌표를 맞춘다
4. `data/reference/korea_outline.geojson`을 레이어로 띄우고, 스냅 도구 모음에서 이 레이어에 꼭짓점·선분 스냅을 켠다
5. 전선을 서쪽 해안에서 동쪽 해안 방향으로 트레이싱한다
6. 내보내기: 레이어 우클릭 → 내보내기 → 다른 이름으로 피처 저장
  - 형식 GeoJSON, 좌표계 EPSG:4326
  - 레이어 옵션 `RFC7946` = YES, `COORDINATE_PRECISION` = 6
  - 파일 이름 `<날짜>.front_line.geojson`
7. `snapshots.csv`에 한 줄 추가한다
8. 교차 검수 자료와 비교하고, 차이는 `note`에 적는다
9. PR (근거 지도와 변경 이유를 PR 설명에 적는다)

## 원본 지도

| 구분 | 자료 | 쓰는 방법 |
| --- | --- | --- |
| 원본 | 미 육군 군사사 센터(CMH) 공간사 3권 지도: *South to the Naktong, North to the Yalu* / *Ebb and Flow* / *Truce Tent and Fighting Front* (퍼블릭 도메인) | 트레이싱 원본 |
| 교차 검수 | 트루먼 도서관 전황 지도 (퍼블릭 도메인), 미 육군사관학교 역사학과 아틀라스 (재사용 시 허락 필요) | 대조 |
| 대조만 | Korean War Project 구글어스 오버레이 (사이트 저작권, 자동 수집 금지) | 손으로 받아 로컬에서 대조만. 저장소에 올리지 않는다 |
| 확인 후 | 위키미디어 커먼즈 월별 전황 지도 | 파일별 라이선스 확인 후 |
| 쓰지 않음 | 변경금지(ND) 지도, 유튜브 영상 화면 | — |

- CMH 지도 목록: https://www.koreanwar.org/html/maps-army.html
- 지도 이미지: 인터넷 아카이브의 CMH Pub 20-2 PDF에는 컬러 지도가 빠져 있다. 공간사 3권의 지도는 [Korean Service](https://www.koreanwaronline.com/history/)의 각 권 목차에서 볼 수 있다. 근거는 그 사이트가 아니라 원본 권과 지도 번호로 적는다.
- 개전 스냅샷(1950-06-25)은 사료가 그날의 경계를 38선으로 기록하므로 위도 38°N을 쓴다 (아래 참고).
- 정전 스냅샷의 군사분계선은 CMH 1953-07-27 지도로 그린다.

## 개전 스냅샷(1950-06-25)

6월 25일 공격 직전의 경계다. 사료가 그날의 경계를 38선으로 기록하므로 위도 38°N을 쓴다.

- Appleman, *South to the Naktong, North to the Yalu*(CMH Pub 20-2) 3장: 국군 4개 사단과 1개 연대가 38선 남쪽을 따라("along the south side of the Parallel") 배치돼 있었고, 북한군의 지상 공격은 38선을 넘어("across the Parallel") 왔다.
- 같은 책 Map I "The North Korean Invasion, 25–28 June 1950": 국군 방어 지역(6월 25일)이 모두 38선 바로 아래에 있고, 북한군 공격 화살표가 38선을 넘는다. 통제선 자체는 선으로 그려져 있지 않다.
- 어긋난 곳: 개성 북쪽 송악산은 38선이 능선을 거의 정확히 따라 지나고, 북한군이 오래전에 점령해 요새화해 두었다(같은 책 3장). 이런 고지 단위 차이(1km 안쪽)는 전국 축척 사료에 그려져 있지 않아 반영하지 않는다.

선은 38°N과 외곽선의 교차점 중 가장 서쪽(옹진반도 서해안 125.117912°E)과 가장 동쪽(동해안 128.749988°E)을 끝점으로 한다. 육지와 겹치는 구간은 옹진반도(약 41km)와 본토(약 262km) 두 곳이고, 사이의 해주만(약 15.6km)은 선을 잇는다. 끝점 사이에는 0.01° 간격으로 점을 넣었다(365점). 두 점만 두면 `::geography`로 계산할 때 선이 대원(최단 경로)으로 해석되어 가운데가 북쪽으로 약 1.55km 휜다.

<details>
<summary>다시 계산하는 방법 (PostGIS)</summary>

```bash
# 외곽선을 outline 테이블로 적재
ogr2ogr -f PostgreSQL PG:dbname=warmap data/reference/korea_outline.geojson \
  -nln outline -nlt MULTIPOLYGON -lco GEOMETRY_NAME=geom
```

```sql
CREATE TABLE front_line_1950_06_25 AS
WITH crossing AS (            -- 38°N과 육지가 겹치는 구간 (옹진반도, 본토)
  SELECT ST_Intersection(geom, ST_GeomFromText('LINESTRING(124 38, 131 38)', 4326)) AS seg
  FROM outline
),
ends AS (                     -- 가장 서쪽·동쪽 교차점 = 해안선 위의 끝점
  SELECT round(ST_XMin(seg)::numeric, 6) AS w, round(ST_XMax(seg)::numeric, 6) AS e
  FROM crossing
),
lons AS (                     -- 끝점 + 그 사이 0.01° 격자
  SELECT w AS lon FROM ends
  UNION ALL
  SELECT g / 100.0 FROM ends, generate_series(ceil(w * 100)::int, floor(e * 100)::int) AS g
  WHERE g / 100.0 > w AND g / 100.0 < e
  UNION ALL
  SELECT e FROM ends
)
SELECT ST_MakeLine(ST_SetSRID(ST_MakePoint(lon::float8, 38), 4326) ORDER BY lon)::geometry(LineString, 4326) AS geom
FROM lons;
```

```bash
ogr2ogr -f GeoJSON data/frontlines/1950-06-25.front_line.geojson PG:dbname=warmap front_line_1950_06_25 \
  -nln 1950-06-25.front_line -lco RFC7946=YES -lco COORDINATE_PRECISION=6
```

</details>
