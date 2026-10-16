# 전선 스냅샷 제작 도구

CMH 지도 이미지에서 전선을 뽑아 `data/frontlines/` 형식(규칙은 [data/frontlines/README.md](../../data/frontlines/README.md))으로 만들고 검증한다. 한 파일(`frontline_tool.py`)에 명령 여섯 개가 있다.

```bash
pip install -r scripts/frontline/requirements.txt   # GDAL(ogr2ogr), PostgreSQL+PostGIS는 따로 설치
python3 scripts/frontline/frontline_tool.py <명령> -h
```

| 명령 | 하는 일 | 결과 |
| --- | --- | --- |
| `sheet` | 지도 일부를 확대하고 원본 픽셀 좌표 격자를 그린다 | 픽셀 위치를 읽는 그림 |
| `fit` | 기준점(픽셀 ↔ 경위도)으로 이미지 → 경위도 변환을 맞춘다 | 변환 JSON, 기준점별 오차, 판정 |
| `trace` | 전선 꼭짓점(픽셀)을 경위도로 옮기고 정리한다 (서→동, 끝점을 해안선에, 0.01° 간격) | `<날짜>.front_line.geojson` |
| `area` | 추가 영역(고립 지역·교두보)의 꼭짓점 고리(`part,u,v`)를 다각형으로 만들고 육지로 자른다 | `<날짜>.extra_areas.geojson` |
| `preview` | 추출한 선을 원본 지도 위에 다시 겹치고, 양측(북 빨강·남 파랑) 지도를 그린다. 추가 영역은 둘러싼 쪽의 반대 색 | PNG 두 장, 양측 면적 |
| `validate` | V1 마이그레이션으로 만든 DB에 `snapshots.csv`와 도형 파일을 모두 넣어 본다 | 끝점·교차·CSV 대응 검사 |

## 순서

1. **지도 이미지:** CMH 공간사의 원본 지도를 가능한 한 고해상도로 받는다. `fit` 결과의 `km_per_px`가 0.5보다 크면(지도 한 픽셀이 500m보다 크면) 쓰지 않는다. 600픽셀급 웹 이미지는 대개 여기서 걸린다.
2. **기준점 CSV** (`name,u,v,lon,lat`): 지도에 표시된 도시처럼 위치가 분명한 점을 6개 이상, 지도 네 귀퉁이 쪽으로 고르게 고른다.
   - `u,v`는 `sheet` 그림에서 읽은 원본 픽셀 좌표다. 같은 점을 확대 배율을 바꿔 두 번 읽고, 2픽셀 넘게 다르면 다시 읽는다.
   - `lon,lat`은 데이터에서 가져온다. 예: Natural Earth `ne_10m_populated_places` v5.1.2. 기억으로 적지 않는다.
3. **`fit`:** 기준점 하나를 빼고 나머지로 맞춘 뒤 뺀 점을 예측한 오차(`loo_km`)를 본다.
   - 판정 기준: `km_per_px` ≤ 0.5, `loo_rms_km` ≤ 1.5km, 각 점 `loo_km` ≤ 3km. `fit`이 "판정: 통과/미달"로 알려 준다.
   - "의심 기준점"으로 나온 점은 다시 읽거나 뺀다.
   - 바다색을 알면 `--sea-rgb`로 해안선 오차도 함께 잰다.
4. **꼭짓점 CSV** (`u,v`): 지도에 그려진 그 날짜의 전선을 서해안에서 동해안까지 따라가며 읽는다. `--snap-rgb`에 전선 색을 주면 꼭짓점이 가까운 전선 픽셀로 옮겨진다.
5. **`trace`:** 끝점이 해안선에서 3km 넘게 떨어져 있으면 멈춘다. 이때는 꼭짓점을 해안까지 이어서 다시 읽는다.
6. **`preview`:** 겹친 그림에서 분홍 선이 지도에 인쇄된 전선과 겹치는지 사람이 확인한다.
7. **`snapshots.csv`**에 한 줄을 추가하고 **`validate`**를 돌린다.
   - `note`에는 "지도 이미지에서 추출, 기준점 N개, loo_rms X km"처럼 방법과 오차를 적는다.

## 예

```bash
T=scripts/frontline/frontline_tool.py
python3 $T sheet raw/map.png work/sheet.png "Seoul:1400,1380,1460,1420" --zoom 8 --step 2
python3 $T fit raw/map.png work/gcps.csv work/fit.json --sea-rgb 190,216,232 --exclude 0,1500,800,2000
python3 $T trace raw/map.png work/fit.json work/vertices.csv data/frontlines/1950-08-04.front_line.geojson
python3 $T preview data/frontlines/1950-08-04.front_line.geojson work/0804 --image raw/map.png --fit work/fit.json \
  --previous data/frontlines/1950-07-20.front_line.geojson
python3 $T validate
```

원본 이미지(`raw/`)와 작업 파일(`work/`)은 저장소에 올리지 않는다. 원본 지도는 `data/raw/`에 둔다.
