# 전선 스냅샷 제작 도구

CMH 지도 이미지에서 전선을 뽑아 `data/frontlines/` 형식(규칙은 [data/frontlines/README.md](../../data/frontlines/README.md))으로 만들고 검증한다. 한 파일(`frontline_tool.py`)에 명령 여덟 개가 있다.

```bash
pip install -r scripts/frontline/requirements.txt   # GDAL(ogr2ogr), PostgreSQL+PostGIS는 따로 설치
python3 scripts/frontline/frontline_tool.py <명령> -h
```

| 명령 | 하는 일 | 결과 |
| --- | --- | --- |
| `sheet` | 지도 일부를 확대하고 원본 픽셀 좌표 격자를 그린다 | 픽셀 위치를 읽는 그림 |
| `fit` | 기준점(픽셀 ↔ 경위도)으로 이미지 → 경위도 변환을 맞춘다 (기본: 횡메르카토르 위에서 affine) | 변환 JSON, 기준점별 오차, 판정 |
| `route` | 경유점 사이를 지도에 그려진 전선 색 픽셀을 따라 자동으로 잇는다 | 꼭짓점 CSV, 틈 목록, 확인 그림 |
| `trace` | 전선 꼭짓점(픽셀)을 경위도로 옮기고 정리한다 (서→동, 끝점을 해안선에, 0.01° 간격) | `<날짜>.front_line.geojson` |
| `area` | 추가 영역(고립 지역·교두보)의 꼭짓점 고리(`part,u,v`)를 다각형으로 만들고 육지로 자른다 | `<날짜>.extra_areas.geojson` |
| `sides` | 화면이 전선 양 끝을 정서·정동으로 늘려 칠할 때 반대쪽 색이 되는 육지를 찾아 추가 영역으로 만든다 (`area` 결과와 합침) | `<날짜>.extra_areas.geojson` |
| `preview` | 추출한 선을 원본 지도 위에 다시 겹치고, 양측(북 빨강·남 파랑) 지도를 그린다. 추가 영역은 둘러싼 쪽의 반대 색 | PNG 두 장, 양측 면적 |
| `validate` | 백엔드 마이그레이션(`V1`, `V2` …)을 모두 적용한 DB에 `snapshots.csv`와 도형 파일을 모두 넣어 본다 | 끝점·교차·CSV 대응 검사 |

## 순서

1. **지도 이미지:** CMH 공간사의 원본 지도를 가능한 한 고해상도로 받는다. `fit` 결과의 `km_per_px`가 0.5보다 크면(지도 한 픽셀이 500m보다 크면) 쓰지 않는다. 600픽셀급 웹 이미지는 대개 여기서 걸린다.
2. **기준점 CSV** (`name,u,v,lon,lat`): 지도에 표시된 도시처럼 위치가 분명한 점을 6개 이상, 지도 네 귀퉁이 쪽으로 고르게 고른다.
   - `u,v`는 `sheet` 그림에서 읽은 원본 픽셀 좌표다. 같은 점을 확대 배율을 바꿔 두 번 읽고, 2픽셀 넘게 다르면 다시 읽는다.
   - `lon,lat`은 데이터에서 가져온다. 예: Natural Earth `ne_10m_populated_places` v5.1.2. 기억으로 적지 않는다.
3. **`fit`:** 기준점 하나를 빼고 나머지로 맞춘 뒤 뺀 점을 예측한 오차(`loo_km`)를 본다.
   - 판정 기준: `km_per_px` ≤ 0.5, `loo_rms_km` ≤ 1.5km, 각 점 `loo_km` ≤ 3km. `fit`이 "판정: 통과/미달"로 알려 준다.
   - "의심 기준점"으로 나온 점은 다시 읽거나 뺀다.
   - 바다색을 알면 `--sea-rgb`로 해안선 오차도 함께 잰다.
   - 경위도는 기준점 가운데를 중심으로 한 횡메르카토르(구면) 평면으로 바꿔 맞춘다(`--proj tm`, 기본). 북한 전체처럼 위도 폭이 넓은 지도에서 38°N 고정 환산(`--proj equirect`, 예전 방식)보다 오차가 작다(2권 Map 3: loo_rms 2.74 → 1.89km). 낙동강 지도처럼 좁은 지도에서는 거의 같다(0.78 → 0.74km). `proj`가 없는 예전 fit.json은 예전 방식으로 읽는다.
4. **꼭짓점 CSV** (`u,v`): 지도에 그려진 그 날짜의 전선을 서해안에서 동해안까지 따라간다. 보통은 `route`로 자동으로 만든다.
   - **경유점 CSV** (`u,v[,join]`, 서→동): 서쪽 해안 끝점과 동쪽 해안 끝점은 꼭 넣는다. 끝점은 지도의 해안선(바다색이 시작하는 곳)까지 찍는다.
   - **`route`:** 전선 색 픽셀(`--line-rgb`, `--rgb-tol`) 위로 경유점 사이의 최소 비용 경로를 구한다. 획 가운데를 따라가고, 강·글자로 끊긴 짧은 틈은 건너간다. 같은 색이라도 막다른 가지(군단 경계 등)는 들어가지 않는다.
   - 전선 색은 전선 위 픽셀 몇 개의 값을 보고 정한다. 도로·글자까지 잡히면 `--rgb-tol`을 줄이고, 획이 많이 끊기면 늘리거나 색을 하나 더 준다. `--check` 그림의 하늘색이 잡힌 픽셀이다.
   - **틈 확인:** `--warn-gap`(기본 15px)을 넘는 틈은 위치와 함께 출력되고 `--check` 그림에 주황 원으로 표시된다. 틈 둘이 가까이 있으면 굽은 곳을 질러갈 수 있으니, 그런 곳은 굽은 곳 위에 경유점을 하나 더 찍는다.
   - **날짜가 다른 선이 같은 색으로 붙어 있으면** 경유점을 더 찍거나, 그 선을 `--block x0,y0,x1,y1` 상자로 막거나, 그 구간만 손으로 읽어 `join=line`(앞 경유점과 직선)으로 넣는다.
   - 손으로만 읽을 때는 꼭짓점 CSV를 바로 쓴다. `trace --snap-rgb`에 전선 색을 주면 꼭짓점이 가까운 전선 픽셀로 옮겨진다(`route` 결과에는 쓰지 않는다).
5. **`trace`:** 끝점이 해안선에서 3km 넘게 떨어져 있으면 멈춘다. 이때는 꼭짓점을 해안까지 이어서 다시 읽는다.
   - **선이 여러 장에 나뉘어 있으면**(예: 1·4 후퇴는 2권 Map 16 서부 + Map 17 동부) 지도마다 `fit`과 꼭짓점 CSV를 따로 만들고, 서쪽 지도를 기본 인자로, 다음 지도를 `--add <fit.json> <vertices.csv>`로 서→동 순서대로 준다. 앞 구간 끝과 다음 구간 첫 점은 직선으로 잇고, 둘이 `--join-km`(기본 10km)보다 멀면 멈춘다. 두 지도에 같이 보이는 곳을 이음점으로 고른다.
6. **`sides`:** 화면은 전선 양 끝을 정서·정동으로 늘려 양측을 나눈다. 그래서 끝점이 남해안이나 하구에 있으면 늘린 선이 지나는 육지가 반대쪽 색이 된다(낙동강 방어선의 남서 해안 등). `sides`가 그 육지를 찾아 추가 영역으로 만든다.
   - 본토: 전선만으로 나눈 조각마다 전선에 길게 맞닿은 쪽의 색이 맞다고 보고, 늘린 선 때문에 떨어져 나간 부분을 고친다.
   - 섬: `--islands-km`(기본 10km) 안에 있는 땅의 색을 따른다. 가까운 섬부터 섬에서 섬으로 이어 가며 정한다. 그 거리 안에 양쪽 땅이 다 있거나(예: 낙동강 방어선 때 거제도) 어느 땅에서도 멀면(예: 개전 때 백령도) 화면 그대로 둔다.
   - 출력 목록을 보고 사료와 다른 곳이 있으면 `area`로 따로 그린다. 고립 지역·교두보가 있으면 `--areas`로 함께 넣는다.
7. **`preview`:** 겹친 그림에서 분홍 선이 지도에 인쇄된 전선과 겹치는지 사람이 확인한다. 추가 영역이 있으면 `--areas`도 준다.
8. **`snapshots.csv`**에 한 줄을 추가하고 **`validate`**를 돌린다.
   - `note`에는 "지도 이미지에서 추출, 기준점 N개, loo_rms X km"처럼 방법과 오차를 적는다.

## 예

```bash
T=scripts/frontline/frontline_tool.py
python3 $T sheet raw/map.png work/sheet.png "Seoul:1400,1380,1460,1420" --zoom 8 --step 2
python3 $T fit raw/map.png work/gcps.csv work/fit.json --sea-rgb 190,216,232 --exclude 0,1500,800,2000
python3 $T route raw/map.png work/waypoints.csv work/vertices.csv --line-rgb 38,48,112 --fit work/fit.json --check work/route.png
python3 $T trace raw/map.png work/fit.json work/vertices.csv data/frontlines/1950-08-04.front_line.geojson
python3 $T sides data/frontlines/1950-08-04.front_line.geojson data/frontlines/1950-08-04.extra_areas.geojson
python3 $T preview data/frontlines/1950-08-04.front_line.geojson work/0804 --image raw/map.png --fit work/fit.json \
  --previous data/frontlines/1950-07-20.front_line.geojson --areas data/frontlines/1950-08-04.extra_areas.geojson
python3 $T validate
```

원본 이미지(`raw/`)와 작업 파일(`work/`)은 저장소에 올리지 않는다. 원본 지도는 `data/raw/`에 둔다.
