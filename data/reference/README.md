# 참조 데이터

전선 데이터를 만들고 계산할 때 기준으로 쓰는 데이터. 직접 고치지 않고, 바꿀 때는 아래 방법으로 다시 만든다.

## `korea_outline.geojson`: 한반도 외곽선

| 항목 | 내용 |
| --- | --- |
| 원본 | [Natural Earth](https://www.naturalearthdata.com/) 1:10m Admin 0 – Countries, v5.1.2 ([GitHub 태그](https://github.com/nvkelso/natural-earth-vector/tree/v5.1.2/10m_cultural)) |
| 라이선스 | 퍼블릭 도메인 ([이용약관](https://www.naturalearthdata.com/about/terms-of-use/)). 출처 표기 의무는 없지만 남겨 둔다 |
| 내용 | 대한민국(KOR)과 북한(PRK) 폴리곤을 합친 MultiPolygon 하나 (섬 포함 62조각, 220,924km²) |
| 좌표 | WGS 84 경위도, 소수 6자리 (원본과의 차이 최대 약 7cm) |
| 쓰는 곳 | 전선 끝점 스냅(QGIS), 38선 계산, 양측 영역 계산, 끝점 검증 |

- 1:10m 축척이라 전국 단위 지도용 정밀도다. 크게 확대하면 해안선이 거칠다.
- 북·중 국경은 현재 국경선이라 1950년과 조금 다를 수 있다. 전국 규모 지도에서는 영향이 작다.

<details>
<summary>다시 만드는 방법</summary>

```bash
# ne_10m_admin_0_countries.{shp,shx,dbf,prj,cpg}를 v5.1.2 태그에서 받은 뒤
ogr2ogr -f PostgreSQL PG:dbname=warmap ne_10m_admin_0_countries.shp \
  -nln ne_countries -nlt PROMOTE_TO_MULTI -lco GEOMETRY_NAME=geom \
  -where "ADM0_A3 IN ('KOR','PRK')" -select ADM0_A3,NAME
```

```sql
CREATE TABLE korea_outline AS
SELECT ST_Multi(ST_Union(geom))::geometry(MultiPolygon, 4326) AS geom
FROM ne_countries;
```

```bash
ogr2ogr -f GeoJSON data/reference/korea_outline.geojson PG:dbname=warmap korea_outline \
  -nln korea_outline -lco RFC7946=YES -lco COORDINATE_PRECISION=6
```

</details>

## `south_korea_outline.geojson`: 남한 외곽선

| 항목 | 내용 |
| --- | --- |
| 원본 | 위와 같은 Natural Earth 1:10m Admin 0 – Countries, v5.1.2 |
| 내용 | 대한민국(KOR) 폴리곤만 담은 MultiPolygon 하나 (섬 포함 53조각, 82KB). 북쪽 경계는 Natural Earth가 그린 현재 군사분계선이다 |
| 좌표 | WGS 84 경위도, 소수 6자리 |
| 쓰는 곳 | 앱 아이콘 배경(`frontend/scripts/make-app-icons.mjs`)만. 1950년대 전선과 섞이므로 지도 화면에는 쓰지 않는다 |

<details>
<summary>다시 만드는 방법</summary>

```bash
# ne_10m_admin_0_countries.{shp,shx,dbf,prj,cpg}를 v5.1.2 태그에서 받은 뒤
ogr2ogr -f GeoJSON data/reference/south_korea_outline.geojson ne_10m_admin_0_countries.shp \
  -nln south_korea_outline -nlt PROMOTE_TO_MULTI -where "ADM0_A3 = 'KOR'" -select "" \
  -lco RFC7946=YES -lco COORDINATE_PRECISION=6
```

</details>

## 프론트 지도 바탕: `frontend/public/geo/`

프론트 지도는 배경 타일을 쓰지 않고 아래 두 파일로 바탕을 그린다. 현대 지도 타일에는 지금의 군사분계선과 지명이 나와 1950년대 전선과 섞이기 때문이다. 둘 다 위 외곽선과 같은 Natural Earth 10m v5.1.2에서 만든다.

| 파일 | 내용 |
| --- | --- |
| `sea_mask.geojson` | 틀(110°E~150°E, 25°N~50°N)에서 한반도 외곽선을 뺀 모양. 전선으로 나눈 양측 색이 육지 밖에 보이지 않게 덮는다. 프론트 `src/lib/geometry.ts`의 `FRAME`과 틀이 같아야 한다 |
| `neighbors.geojson` | 틀 안의 한반도 외 나라(중국·러시아·일본 등)를 합친 것. 한반도와 맞닿는 국경은 원본 그대로 두고, 한반도에서 0.1° 넘게 떨어진 해안선만 약 500m 단위로 단순화했다 (205KB) |

<details>
<summary>다시 만드는 방법</summary>

```bash
# 외곽선과 나라 경계를 적재 (ne_10m_admin_0_countries는 위와 같은 v5.1.2 태그)
ogr2ogr -f PostgreSQL PG:dbname=warmap data/reference/korea_outline.geojson \
  -nln outline -nlt MULTIPOLYGON -lco GEOMETRY_NAME=geom
ogr2ogr -f PostgreSQL PG:dbname=warmap ne_10m_admin_0_countries.shp \
  -nln ne_countries -nlt PROMOTE_TO_MULTI -lco GEOMETRY_NAME=geom -select ADM0_A3,NAME -spat 105 20 155 55
```

```sql
CREATE TABLE frame AS SELECT ST_MakeEnvelope(110, 25, 150, 50, 4326) AS geom;

CREATE TABLE sea_mask AS
SELECT ST_Multi(ST_Difference(f.geom, o.geom))::geometry(MultiPolygon, 4326) AS geom
FROM frame f, outline o;

CREATE TABLE neighbors_full AS
SELECT ST_Multi(ST_CollectionExtract(ST_Intersection(ST_Union(c.geom), f.geom), 3))::geometry(MultiPolygon, 4326) AS geom
FROM ne_countries c, frame f
WHERE c.adm0_a3 NOT IN ('KOR', 'PRK')
GROUP BY f.geom;

-- 단순화한 것과 한반도 주변 0.1°의 원본을 합친 뒤 한반도를 빼서, 국경에 틈이나 겹침이 생기지 않게 한다
CREATE TABLE neighbors AS
SELECT ST_Multi(ST_CollectionExtract(ST_Difference(
         ST_Union(ST_SimplifyPreserveTopology(n.geom, 0.005), ST_Intersection(n.geom, ST_Buffer(o.geom, 0.1))),
         o.geom), 3))::geometry(MultiPolygon, 4326) AS geom
FROM neighbors_full n, outline o;
```

```bash
ogr2ogr -f GeoJSON frontend/public/geo/sea_mask.geojson PG:dbname=warmap sea_mask \
  -nln sea_mask -lco RFC7946=YES -lco COORDINATE_PRECISION=6
ogr2ogr -f GeoJSON frontend/public/geo/neighbors.geojson PG:dbname=warmap neighbors \
  -nln neighbors -lco RFC7946=YES -lco COORDINATE_PRECISION=6
```

</details>
