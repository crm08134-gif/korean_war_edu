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
