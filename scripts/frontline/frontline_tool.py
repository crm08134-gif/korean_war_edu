#!/usr/bin/env python3
"""전선 스냅샷 제작 도구.

CMH 지도 이미지에서 전선을 뽑아 data/frontlines/ 형식의 GeoJSON으로 만들고 검증한다.
명령마다 하는 일은 README.md 참고. 좌표는 기억으로 만들지 않는다: 기준점의 경위도는
Natural Earth 같은 데이터에서, 전선의 픽셀 위치는 지도 이미지에서 가져온다.

  sheet     확대 격자 그림 (픽셀 위치를 읽기 위한 것)
  fit       기준점(픽셀 ↔ 경위도)으로 이미지 → 경위도 변환을 맞추고 오차를 보고
  trace     전선 꼭짓점(픽셀)을 경위도로 옮겨 정리하고 GeoJSON으로 저장
  area      추가 영역(고립 지역·교두보) 꼭짓점 고리를 다각형으로 저장
  preview   추출한 선을 원본 지도 위에 다시 겹친 그림, 양측(북 빨강·남 파랑) 지도
  validate  V1 마이그레이션으로 만든 DB에 snapshots.csv와 도형 파일을 넣어 검증

필요: python3, numpy, pillow, shapely, opencv-python-headless, GDAL(ogr2ogr), PostgreSQL+PostGIS(validate)
"""
import argparse
import csv
import json
import math
import os
import subprocess
import sys
import tempfile

import numpy as np
from PIL import Image, ImageDraw
from shapely.geometry import LineString, MultiPolygon, Point, Polygon, shape
from shapely.ops import nearest_points

LON0, LAT0 = 127.0, 38.0                      # 거리 계산용 국지 좌표 원점
KM_LON = 111.32 * math.cos(math.radians(LAT0))  # 경도 1도 = km (38°N)
KM_LAT = 110.57                                  # 위도 1도 = km
DEFAULT_OUTLINE = 'data/reference/korea_outline.geojson'


def load_rgb(path):
    with Image.open(path) as im:
        return im.convert('RGB')


def read_json(path):
    with open(path, encoding='utf-8') as f:
        return json.load(f)


def to_km(lon, lat):
    return (np.asarray(lon) - LON0) * KM_LON, (np.asarray(lat) - LAT0) * KM_LAT


def from_km(x, y):
    return np.asarray(x) / KM_LON + LON0, np.asarray(y) / KM_LAT + LAT0


def load_outline(path):
    gj = read_json(path)
    geoms = [shape(f['geometry']) for f in gj['features']]
    polys = []
    for g in geoms:
        polys.extend(g.geoms if isinstance(g, MultiPolygon) else [g])
    return MultiPolygon(polys)


# ---------------------------------------------------------------- sheet
def cmd_sheet(a):
    src = load_rgb(a.image)
    tiles = []
    for spec in a.boxes:
        name, box = spec.split(':')
        x0, y0, x1, y1 = map(int, box.split(','))
        t = src.crop((x0, y0, x1, y1)).resize(((x1 - x0) * a.zoom, (y1 - y0) * a.zoom), Image.NEAREST)
        d = ImageDraw.Draw(t)
        for x in range(x0 - x0 % a.step + a.step, x1, a.step):
            X = (x - x0) * a.zoom
            strong = x % (a.step * 5) == 0
            d.line([(X, 0), (X, t.height)], fill=(255, 0, 255) if strong else (0, 170, 0))
            if strong:
                d.text((X + 2, 2), str(x), fill=(200, 0, 200))
        for y in range(y0 - y0 % a.step + a.step, y1, a.step):
            Y = (y - y0) * a.zoom
            strong = y % (a.step * 5) == 0
            d.line([(0, Y), (t.width, Y)], fill=(255, 0, 255) if strong else (0, 170, 0))
            if strong:
                d.text((2, Y + 2), str(y), fill=(200, 0, 200))
        tile = Image.new('RGB', (t.width, t.height + 16), 'white')
        ImageDraw.Draw(tile).text((4, 2), name, fill='black')
        tile.paste(t, (0, 16))
        tiles.append(tile)
    cols = min(a.cols, len(tiles))
    cw, rh = max(t.width for t in tiles) + 8, max(t.height for t in tiles) + 8
    sheet = Image.new('RGB', (cw * cols, rh * ((len(tiles) + cols - 1) // cols)), 'white')
    for i, t in enumerate(tiles):
        sheet.paste(t, ((i % cols) * cw, (i // cols) * rh))
    sheet.save(a.out)
    print(f'{a.out}: {len(tiles)} tiles')


# ---------------------------------------------------------------- fit
def read_gcps(path):
    with open(path, encoding='utf-8') as f:
        rows = list(csv.DictReader(f))
    for r in rows:
        r['u'], r['v'], r['lon'], r['lat'] = (float(r[k]) for k in ('u', 'v', 'lon', 'lat'))
    return rows


def solve(model, uv, xy):
    """픽셀(uv) → 국지 km(xy) 3x3 행렬. affine: 최소제곱, homography: DLT."""
    n = len(uv)
    if model == 'affine':
        A = np.column_stack([uv, np.ones(n)])
        cx, *_ = np.linalg.lstsq(A, xy[:, 0], rcond=None)
        cy, *_ = np.linalg.lstsq(A, xy[:, 1], rcond=None)
        return np.array([cx, cy, [0, 0, 1.0]])
    rows = []
    for (u, v), (x, y) in zip(uv, xy):
        rows.append([u, v, 1, 0, 0, 0, -x * u, -x * v, -x])
        rows.append([0, 0, 0, u, v, 1, -y * u, -y * v, -y])
    _, _, vt = np.linalg.svd(np.array(rows))
    H = vt[-1].reshape(3, 3)
    return H / H[2, 2]


def apply(H, u, v):
    p = H @ np.vstack([np.asarray(u, float), np.asarray(v, float), np.ones(np.size(u))])
    return p[0] / p[2], p[1] / p[2]


def cmd_fit(a):
    g = read_gcps(a.gcps)
    uv = np.array([[r['u'], r['v']] for r in g])
    xy = np.column_stack(to_km([r['lon'] for r in g], [r['lat'] for r in g]))
    need = 3 if a.model == 'affine' else 4
    if len(g) < need + 2:
        sys.exit(f'기준점이 {len(g)}개뿐이다. {a.model}에는 최소 {need + 2}개가 필요하다 (오차 검사용 여유 2개 포함).')
    H = solve(a.model, uv, xy)
    px, py = apply(H, uv[:, 0], uv[:, 1])
    res = np.hypot(px - xy[:, 0], py - xy[:, 1])
    loo = []
    for i in range(len(g)):
        m = np.arange(len(g)) != i
        Hi = solve(a.model, uv[m], xy[m])
        qx, qy = apply(Hi, uv[i:i + 1, 0], uv[i:i + 1, 1])
        loo.append(float(np.hypot(qx[0] - xy[i, 0], qy[0] - xy[i, 1])))
    w, h = load_rgb(a.image).size
    corners = apply(H, [0, w, w, 0], [0, 0, h, h])
    cx, cy = apply(H, [w / 2, w / 2 + 1], [h / 2, h / 2])
    km_per_px = float(np.hypot(cx[1] - cx[0], cy[1] - cy[0]))
    report = {
        'image': os.path.basename(a.image), 'image_size': [w, h], 'model': a.model,
        'matrix_px_to_km': H.tolist(), 'origin_lonlat': [LON0, LAT0],
        'km_per_px': round(km_per_px, 3),
        'rms_km': round(float(np.sqrt(np.mean(res ** 2))), 2),
        'loo_rms_km': round(float(np.sqrt(np.mean(np.square(loo)))), 2),
        'points': [{'name': r['name'], 'u': r['u'], 'v': r['v'], 'res_km': round(float(e), 2), 'loo_km': round(l, 2)}
                   for r, e, l in zip(g, res, loo)],
        'corners_lonlat': [[round(float(x), 4), round(float(y), 4)] for x, y in zip(*from_km(*corners))],
    }
    if a.outline and a.sea_rgb:
        report['coast_check'] = coast_check(a, H)
    with open(a.out, 'w', encoding='utf-8') as f:
        json.dump(report, f, ensure_ascii=False, indent=1)
    print(json.dumps({k: v for k, v in report.items() if k != 'matrix_px_to_km'}, ensure_ascii=False, indent=1))
    bad = [p['name'] for p in report['points'] if p['loo_km'] > max(3 * np.median(loo), 2 * a.tol_km)]
    if bad:
        print(f'의심 기준점 (다시 읽을 것): {", ".join(bad)}')
    fails = []
    if report['km_per_px'] > a.max_km_per_px:
        fails.append(f'지도 해상도 {report["km_per_px"]}km/px > {a.max_km_per_px} (더 큰 이미지 필요)')
    if report['loo_rms_km'] > a.tol_km:
        fails.append(f'loo_rms {report["loo_rms_km"]}km > {a.tol_km}km')
    worst = max(p['loo_km'] for p in report['points'])
    if worst > 2 * a.tol_km:
        fails.append(f'가장 나쁜 기준점 loo {worst}km > {2 * a.tol_km}km')
    print('판정:', '통과' if not fails else '미달 - ' + '; '.join(fails))


def coast_check(a, H):
    """지도에서 바다색 영역의 경계를 경위도로 옮겨 외곽선 해안선과의 거리를 잰다."""
    import cv2
    img = np.asarray(load_rgb(a.image)).astype(int)
    rgb = np.array([int(c) for c in a.sea_rgb.split(',')])
    sea = (np.abs(img - rgb).sum(axis=2) <= a.sea_tol).astype(np.uint8)
    sea = cv2.morphologyEx(sea, cv2.MORPH_OPEN, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5)))
    edge = cv2.morphologyEx(sea, cv2.MORPH_GRADIENT, np.ones((3, 3), np.uint8)) > 0
    edge[:5, :] = edge[-5:, :] = edge[:, :5] = edge[:, -5:] = False
    for box in a.exclude or []:
        x0, y0, x1, y1 = map(int, box.split(','))
        edge[y0:y1, x0:x1] = False
    v, u = np.nonzero(edge)
    if len(u) == 0:
        return {'n': 0}
    lon, lat = from_km(*apply(H, u, v))
    coast = load_outline(a.outline).boundary
    pts = np.array(coast.coords) if coast.geom_type == 'LineString' else np.vstack([np.array(l.coords) for l in coast.geoms])
    from scipy.spatial import cKDTree
    tree = cKDTree(np.column_stack(to_km(pts[:, 0], pts[:, 1])))
    d, _ = tree.query(np.column_stack(to_km(lon, lat)))
    return {'n': int(len(d)), 'median_km': round(float(np.median(d)), 2), 'p90_km': round(float(np.percentile(d, 90)), 2)}


# ---------------------------------------------------------------- trace
def snap_to_color(img, u, v, rgb, tol, radius):
    """꼭짓점을 반경 안에서 전선 색에 가장 가까운 픽셀 무리의 중심으로 옮긴다."""
    h, w = img.shape[:2]
    out = []
    for x, y in zip(u, v):
        x0, x1 = max(0, int(x) - radius), min(w, int(x) + radius + 1)
        y0, y1 = max(0, int(y) - radius), min(h, int(y) + radius + 1)
        patch = img[y0:y1, x0:x1].astype(int)
        m = np.abs(patch - rgb).sum(axis=2) <= tol
        if m.any():
            yy, xx = np.nonzero(m)
            out.append((x0 + xx.mean(), y0 + yy.mean()))
        else:
            out.append((x, y))
    return np.array(out)


def cmd_trace(a):
    fit = read_json(a.fit)
    H = np.array(fit['matrix_px_to_km'])
    with open(a.vertices, encoding='utf-8') as f:
        pts = [(float(r['u']), float(r['v'])) for r in csv.DictReader(f)]
    uv = np.array(pts)
    if a.snap_rgb:
        img = np.asarray(load_rgb(a.image))
        uv = snap_to_color(img, uv[:, 0], uv[:, 1], np.array([int(c) for c in a.snap_rgb.split(',')]), a.snap_tol, a.snap_radius)
    lon, lat = from_km(*apply(H, uv[:, 0], uv[:, 1]))
    line = LineString(np.column_stack([lon, lat]))
    if line.coords[0][0] > line.coords[-1][0]:
        line = LineString(list(line.coords)[::-1])          # 서→동
    land = load_outline(a.outline)
    coast = land.boundary
    coords = list(line.coords)
    for idx in (0, -1):                                     # 끝점을 해안선에 붙인다
        p = Point(coords[idx])
        q = nearest_points(coast, p)[0]
        dkm = math.hypot((p.x - q.x) * KM_LON, (p.y - q.y) * KM_LAT)
        if dkm > a.snap_km:
            sys.exit(f'{"서" if idx == 0 else "동"}쪽 끝점이 해안선에서 {dkm:.1f}km 떨어져 있다 (허용 {a.snap_km}km). 꼭짓점을 해안까지 이어서 다시 읽을 것.')
        print(f'{"서" if idx == 0 else "동"}쪽 끝점: 해안선까지 {dkm:.2f}km → 해안선 위로 옮김')
        coords[idx] = (q.x, q.y)
    line = LineString(coords).segmentize(0.01)
    if not line.is_simple:
        sys.exit('선이 스스로 교차한다. 꼭짓점 순서를 확인할 것.')
    line = LineString([(round(x, 6), round(y, 6)) for x, y in line.coords])
    write_geojson([line], a.out)
    km = sum(math.hypot((x2 - x1) * KM_LON, (y2 - y1) * KM_LAT) for (x1, y1), (x2, y2) in zip(line.coords[:-1], line.coords[1:]))
    print(f'{a.out}: {len(line.coords)}점, 길이 {km:.0f}km, 서쪽 끝 {line.coords[0]}, 동쪽 끝 {line.coords[-1]}')


def write_geojson(geoms, out):
    """QGIS(GDAL) 내보내기와 같은 형식으로 쓴다: RFC 7946, 소수 6자리, properties 비움."""
    from shapely.geometry import mapping
    name = os.path.basename(out).replace('.geojson', '')
    with tempfile.NamedTemporaryFile('w', suffix='.geojson', delete=False, encoding='utf-8') as tmp:
        json.dump({'type': 'FeatureCollection', 'features': [
            {'type': 'Feature', 'properties': {}, 'geometry': mapping(g)} for g in geoms]}, tmp)
    if os.path.exists(out):
        os.remove(out)
    subprocess.run(['ogr2ogr', '-f', 'GeoJSON', out, tmp.name, '-nln', name,
                    '-lco', 'RFC7946=YES', '-lco', 'COORDINATE_PRECISION=6'], check=True)
    os.remove(tmp.name)


def cmd_area(a):
    """추가 영역(고립 지역·교두보): part별 꼭짓점 고리를 다각형으로 만들어 육지로 자른다."""
    fit = read_json(a.fit)
    H = np.array(fit['matrix_px_to_km'])
    parts = {}
    with open(a.vertices, encoding='utf-8') as f:
        for r in csv.DictReader(f):
            parts.setdefault(r['part'], []).append((float(r['u']), float(r['v'])))
    land = load_outline(a.outline)
    polys = []
    for name, pts in parts.items():
        uv = np.array(pts)
        lon, lat = from_km(*apply(H, uv[:, 0], uv[:, 1]))
        poly = Polygon(np.column_stack([lon, lat])).buffer(0).intersection(land)
        for p in getattr(poly, 'geoms', [poly]):
            if p.geom_type == 'Polygon' and not p.is_empty:
                p = Polygon(p.exterior.coords, [i.coords for i in p.interiors]).segmentize(0.01)
                p = Polygon([(round(x, 6), round(y, 6)) for x, y in p.exterior.coords],
                            [[(round(x, 6), round(y, 6)) for x, y in i.coords] for i in p.interiors])
                polys.append(p)
                print(f'{name}: {area_km2(p):,.1f}km²')
    union_area = area_km2(MultiPolygon(polys).buffer(0))
    if abs(union_area - sum(area_km2(p) for p in polys)) > 1:
        sys.exit('추가 영역끼리 겹친다. 꼭짓점을 확인할 것.')
    write_geojson(polys, a.out)
    print(f'{a.out}: 다각형 {len(polys)}개')


# ---------------------------------------------------------------- preview
def split_sides(land, line):
    """전선 끝을 정서·정동으로 늘린 반평면으로 육지를 북측/남측으로 나눈다."""
    c = list(line.coords)
    ring = [(115.0, c[0][1])] + c + [(140.0, c[-1][1]), (140.0, 46.0), (115.0, 46.0)]
    north_hp = Polygon(ring).buffer(0)
    return land.intersection(north_hp), land.difference(north_hp)


def area_km2(geom):
    """면적 보존(사인 곡선 도법, 구면) 근사. PostGIS geography 값과 1% 안쪽으로 맞는다."""
    import shapely
    k = 6371.0088 * math.pi / 180

    def sinus(c):
        return np.column_stack(((c[:, 0] - LON0) * k * np.cos(np.radians(c[:, 1])), c[:, 1] * k))
    return shapely.transform(geom, sinus).area


def cmd_preview(a):
    line = shape(read_json(a.line)['features'][0]['geometry'])
    land = load_outline(a.outline)
    outs = []
    if a.image and a.fit:                                   # 1) 원본 지도 위에 되돌려 겹치기
        fit = read_json(a.fit)
        Hinv = np.linalg.inv(np.array(fit['matrix_px_to_km']))
        img = load_rgb(a.image)
        scale = max(1, 1600 // img.width)
        img = img.resize((img.width * scale, img.height * scale), Image.LANCZOS)
        d = ImageDraw.Draw(img)
        xs, ys = to_km(*np.array(line.coords).T)
        u, v = apply(Hinv, xs, ys)
        d.line(list(zip(u * scale, v * scale)), fill=(255, 0, 200), width=max(2, scale))
        for p in fit['points']:
            r = 4 * scale
            d.ellipse([p['u'] * scale - r, p['v'] * scale - r, p['u'] * scale + r, p['v'] * scale + r], outline=(0, 160, 0), width=2)
            d.text((p['u'] * scale + r + 2, p['v'] * scale - r), f"{p['name']} {p['loo_km']}km", fill=(0, 120, 0))
        path = a.prefix + '_overlay.png'
        img.save(path)
        outs.append(path)
    north, south = split_sides(land, line)                  # 2) 양측 지도
    W, Hh = 700, 900
    x0, x1, y0, y1 = 123.8, 131.0, 33.0, 43.2

    def px(lon, lat):
        return ((lon - x0) / (x1 - x0) * W, (y1 - lat) / (y1 - y0) * Hh)
    canvas = Image.new('RGB', (W, Hh), (233, 237, 241))
    d = ImageDraw.Draw(canvas, 'RGBA')

    def fill(geom, color):
        for p in getattr(geom, 'geoms', [geom]):
            if p.is_empty or p.geom_type != 'Polygon':
                continue
            d.polygon([px(x, y) for x, y in p.exterior.coords], fill=color, outline=(90, 90, 90, 255))
    fill(north, (210, 69, 59, 170))
    fill(south, (47, 109, 181, 160))
    if a.areas:                                             # 추가 영역은 둘러싼 쪽의 반대 색
        for f in read_json(a.areas)['features']:
            g = shape(f['geometry'])
            in_north = north.contains(g.representative_point())
            fill(g, (47, 109, 181, 230) if in_north else (210, 69, 59, 230))
    if a.previous:
        prev = shape(read_json(a.previous)['features'][0]['geometry'])
        pts = [px(x, y) for x, y in prev.coords]
        for i in range(0, len(pts) - 1, 2):
            d.line([pts[i], pts[i + 1]], fill=(40, 40, 40, 255), width=2)
    d.line([px(x, y) for x, y in line.coords], fill=(20, 20, 20, 255), width=3)
    # 그림 속 글자는 한글 글꼴이 없는 환경에서도 깨지지 않게 영어로 쓴다
    d.text((10, 10), f"{os.path.basename(a.line)}  North {area_km2(north):,.0f} km2 / South {area_km2(south):,.0f} km2",
           fill=(0, 0, 0, 255))
    if a.previous:
        d.text((10, 26), f"dashed: {os.path.basename(a.previous)}", fill=(0, 0, 0, 255))
    path = a.prefix + '_sides.png'
    canvas.save(path)
    outs.append(path)
    print('\n'.join(outs))
    print(f'북측 {area_km2(north):,.1f}km², 남측 {area_km2(south):,.1f}km²')


# ---------------------------------------------------------------- validate
def cmd_validate(a):
    def psql(*args, **kw):
        return subprocess.run(['psql', '-X', '-q', '-v', 'ON_ERROR_STOP=1', *args], capture_output=True, text=True, **kw)
    psql('-d', 'postgres', '-c', f'DROP DATABASE IF EXISTS {a.db}')
    r = psql('-d', 'postgres', '-c', f'CREATE DATABASE {a.db}')
    if r.returncode:
        sys.exit(r.stderr)
    r = psql('-d', a.db, '-f', a.migration)
    if r.returncode:
        sys.exit('마이그레이션 실패: ' + r.stderr)
    subprocess.run(['ogr2ogr', '-f', 'PostgreSQL', f'PG:dbname={a.db}', a.outline, '-nln', 'outline',
                    '-nlt', 'MULTIPOLYGON', '-lco', 'GEOMETRY_NAME=geom'], check=True)
    root = a.dir
    with open(os.path.join(root, 'snapshots.csv'), 'rb') as f:
        raw = f.read()
    problems = []
    if raw.startswith(b'\xef\xbb\xbf') or b'\r' in raw:
        problems.append('snapshots.csv: BOM이나 CRLF가 있다 (UTF-8, LF로 저장)')
    rows = list(csv.DictReader(raw.decode('utf-8').splitlines(), strict=True))
    files = {f.split('.')[0] for f in os.listdir(root) if f.endswith('.front_line.geojson')}
    dates = [r['snapshot_date'] for r in rows]
    if sorted(set(dates) - files):
        problems.append(f'CSV에만 있는 날짜: {sorted(set(dates) - files)}')
    if sorted(files - set(dates)):
        problems.append(f'파일만 있는 날짜: {sorted(files - set(dates))}')
    for row in rows:
        d = row['snapshot_date']
        fc = read_json(os.path.join(root, f'{d}.front_line.geojson'))
        if len(fc['features']) != 1:
            problems.append(f'{d}: front_line 파일의 Feature가 {len(fc["features"])}개다 (1개여야 한다)')
            continue
        geom = json.dumps(fc['features'][0]['geometry'])
        areas = 'NULL'
        ap = os.path.join(root, f'{d}.extra_areas.geojson')
        if os.path.exists(ap):
            parts = [json.dumps(f['geometry']) for f in read_json(ap)['features']]
            areas = 'ST_Multi(ST_Collect(ARRAY[' + ','.join(f"ST_GeomFromGeoJSON($j${p}$j$)" for p in parts) + ']))'
        sql = (f"INSERT INTO frontline_snapshot (snapshot_date, front_line, extra_areas, source_map, note) VALUES "
               f"(:'d'::date, ST_GeomFromGeoJSON(:'g'), {areas}, :'s', NULLIF(:'n',''))")
        r = psql('-d', a.db, '-v', f'd={d}', '-v', f'g={geom}', '-v', f"s={row['source_map']}", '-v', f"n={row['note']}", input=sql)
        if r.returncode:
            problems.append(f'{d}: DB 제약 위반 {r.stderr.strip()}')
            continue
        chk = psql('-d', a.db, '-At', '-F', '|', '-c', f"""
            SELECT ST_NPoints(front_line),
                   round(ST_Distance(ST_StartPoint(front_line)::geography, ST_Boundary(o.geom)::geography)::numeric, 1),
                   round(ST_Distance(ST_EndPoint(front_line)::geography, ST_Boundary(o.geom)::geography)::numeric, 1),
                   ST_IsSimple(front_line),
                   round((ST_Length(ST_Difference(front_line, o.geom)::geography) / 1000)::numeric, 1)
            FROM frontline_snapshot, outline o WHERE snapshot_date = '{d}'""")
        n, ws, es, simple, sea_km = chk.stdout.strip().split('|')
        print(f'{d}: {n}점, 끝점-해안선 서 {ws}m 동 {es}m, 단순선 {simple}, 바다 위 구간 {sea_km}km')
        if float(ws) > a.end_m or float(es) > a.end_m:
            problems.append(f'{d}: 끝점이 해안선에서 {a.end_m}m 넘게 떨어져 있다')
        if simple != 't':
            problems.append(f'{d}: 선이 스스로 교차한다')
    print('문제 없음' if not problems else '\n'.join('문제: ' + p for p in problems))
    sys.exit(1 if problems else 0)


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest='cmd', required=True)
    s = sub.add_parser('sheet'); s.add_argument('image'); s.add_argument('out')
    s.add_argument('boxes', nargs='+', help='이름:x0,y0,x1,y1'); s.add_argument('--zoom', type=int, default=6)
    s.add_argument('--step', type=int, default=5); s.add_argument('--cols', type=int, default=3)
    s = sub.add_parser('fit'); s.add_argument('image'); s.add_argument('gcps', help='CSV: name,u,v,lon,lat')
    s.add_argument('out'); s.add_argument('--model', choices=['affine', 'homography'], default='affine')
    s.add_argument('--tol-km', type=float, default=1.5); s.add_argument('--max-km-per-px', type=float, default=0.5)
    s.add_argument('--outline', default=DEFAULT_OUTLINE)
    s.add_argument('--sea-rgb', help='해안선 검사용 바다색 R,G,B'); s.add_argument('--sea-tol', type=int, default=40)
    s.add_argument('--exclude', nargs='*', help='해안선 검사에서 뺄 상자 x0,y0,x1,y1 (범례 등)')
    s = sub.add_parser('trace'); s.add_argument('image'); s.add_argument('fit'); s.add_argument('vertices', help='CSV: u,v (서→동 순서 권장)')
    s.add_argument('out', help='<날짜>.front_line.geojson'); s.add_argument('--outline', default=DEFAULT_OUTLINE)
    s.add_argument('--snap-km', type=float, default=3.0); s.add_argument('--snap-rgb', help='전선 색 R,G,B')
    s.add_argument('--snap-tol', type=int, default=60); s.add_argument('--snap-radius', type=int, default=4)
    s = sub.add_parser('area'); s.add_argument('fit'); s.add_argument('vertices', help='CSV: part,u,v (part마다 고리 하나)')
    s.add_argument('out', help='<날짜>.extra_areas.geojson'); s.add_argument('--outline', default=DEFAULT_OUTLINE)
    s = sub.add_parser('preview'); s.add_argument('line'); s.add_argument('prefix')
    s.add_argument('--image'); s.add_argument('--fit'); s.add_argument('--previous', help='직전 스냅샷 front_line 파일')
    s.add_argument('--areas', help='같은 날짜의 extra_areas 파일')
    s.add_argument('--outline', default=DEFAULT_OUTLINE)
    s = sub.add_parser('validate'); s.add_argument('--dir', default='data/frontlines')
    s.add_argument('--migration', default='backend/src/main/resources/db/migration/V1__create_frontline_snapshot.sql')
    s.add_argument('--outline', default=DEFAULT_OUTLINE); s.add_argument('--db', default='frontline_check')
    s.add_argument('--end-m', type=float, default=100.0)
    a = p.parse_args()
    {'sheet': cmd_sheet, 'fit': cmd_fit, 'trace': cmd_trace, 'area': cmd_area, 'preview': cmd_preview, 'validate': cmd_validate}[a.cmd](a)


if __name__ == '__main__':
    main()
