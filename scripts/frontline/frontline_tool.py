#!/usr/bin/env python3
"""전선 스냅샷 제작 도구.

CMH 지도 이미지에서 전선을 뽑아 data/frontlines/ 형식의 GeoJSON으로 만들고 검증한다.
명령마다 하는 일은 README.md 참고. 좌표는 기억으로 만들지 않는다: 기준점의 경위도는
Natural Earth 같은 데이터에서, 전선의 픽셀 위치는 지도 이미지에서 가져온다.

  sheet     확대 격자 그림 (픽셀 위치를 읽기 위한 것)
  fit       기준점(픽셀 ↔ 경위도)으로 이미지 → 경위도 변환을 맞추고 오차를 보고
  route     경유점 사이를 지도에 그려진 전선 색 픽셀을 따라 자동으로 이어 꼭짓점 CSV를 만든다
  trace     전선 꼭짓점(픽셀)을 경위도로 옮겨 정리하고 GeoJSON으로 저장
  area      추가 영역(고립 지역·교두보) 꼭짓점 고리를 다각형으로 저장
  sides     화면이 전선 양 끝을 정서·정동으로 늘려 칠할 때 반대쪽 색이 되는 육지를 추가 영역으로 저장
  preview   추출한 선을 원본 지도 위에 다시 겹친 그림, 양측(북 빨강·남 파랑) 지도
  validate  백엔드 마이그레이션(V1, V2 …)을 모두 적용한 DB에 snapshots.csv와 도형 파일을 넣어 검증

필요: python3, numpy, pillow, shapely, scipy, opencv-python-headless, scikit-image(route), GDAL(ogr2ogr), PostgreSQL+PostGIS(validate)
"""
import argparse
import csv
import json
import math
import os
import re
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


# 이미지 ↔ 경위도 변환에 쓰는 평면 좌표. fit이 고르고 fit.json의 'proj'에 남긴다.
#   tm:       기준점 가운데를 중심으로 한 횡메르카토르(구면). 지도처럼 각이 보존되는 투영이라
#             위도 폭이 넓은 지도(북한 전체 등)에서도 affine 하나로 잘 맞는다.
#   equirect: 38°N 고정 환산(to_km). 'proj'가 없는 예전 fit.json은 이것으로 읽는다.
R_KM = 6371.0088


def tm_fwd(lon, lat, lon0, lat0):
    lam = np.radians(np.asarray(lon, float) - lon0)
    phi = np.radians(np.asarray(lat, float))
    b = np.cos(phi) * np.sin(lam)
    return R_KM * np.arctanh(b), R_KM * (np.arctan2(np.tan(phi), np.cos(lam)) - math.radians(lat0))


def tm_inv(x, y, lon0, lat0):
    d = np.asarray(y, float) / R_KM + math.radians(lat0)
    xr = np.asarray(x, float) / R_KM
    return lon0 + np.degrees(np.arctan2(np.sinh(xr), np.cos(d))), np.degrees(np.arcsin(np.sin(d) / np.cosh(xr)))


def proj_of(fit):
    return fit.get('proj') or {'type': 'equirect'}


def proj_fwd(proj, lon, lat):
    if proj['type'] == 'tm':
        return tm_fwd(lon, lat, proj['lon0'], proj['lat0'])
    return to_km(lon, lat)


def proj_inv(proj, x, y):
    if proj['type'] == 'tm':
        return tm_inv(x, y, proj['lon0'], proj['lat0'])
    return from_km(x, y)


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
    lons, lats = [r['lon'] for r in g], [r['lat'] for r in g]
    proj = ({'type': 'tm', 'lon0': round(float(np.mean(lons)), 1), 'lat0': round(float(np.mean(lats)), 1)}
            if a.proj == 'tm' else {'type': 'equirect', 'origin_lonlat': [LON0, LAT0]})
    xy = np.column_stack(proj_fwd(proj, lons, lats))
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
        'matrix_px_to_km': H.tolist(), 'proj': proj,
        'km_per_px': round(km_per_px, 3),
        'rms_km': round(float(np.sqrt(np.mean(res ** 2))), 2),
        'loo_rms_km': round(float(np.sqrt(np.mean(np.square(loo)))), 2),
        'points': [{'name': r['name'], 'u': r['u'], 'v': r['v'], 'res_km': round(float(e), 2), 'loo_km': round(l, 2)}
                   for r, e, l in zip(g, res, loo)],
        'corners_lonlat': [[round(float(x), 4), round(float(y), 4)] for x, y in zip(*proj_inv(proj, *corners))],
    }
    if a.outline and a.sea_rgb:
        report['coast_check'] = coast_check(a, H, proj)
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


def coast_check(a, H, proj):
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
    lon, lat = proj_inv(proj, *apply(H, u, v))
    coast = load_outline(a.outline).boundary
    pts = np.array(coast.coords) if coast.geom_type == 'LineString' else np.vstack([np.array(l.coords) for l in coast.geoms])
    from scipy.spatial import cKDTree
    tree = cKDTree(np.column_stack(proj_fwd(proj, pts[:, 0], pts[:, 1])))
    d, _ = tree.query(np.column_stack(proj_fwd(proj, lon, lat)))
    return {'n': int(len(d)), 'median_km': round(float(np.median(d)), 2), 'p90_km': round(float(np.percentile(d, 90)), 2)}


# ---------------------------------------------------------------- route
def line_mask(img, colors, tol):
    """전선 색(여러 개 줄 수 있음) 중 하나와 R·G·B 차이의 합이 tol 이하인 픽셀."""
    img = img.astype(int)
    mask = np.zeros(img.shape[:2], bool)
    for c in colors:
        mask |= np.abs(img - c).sum(axis=2) <= tol
    return mask


def gaps_on(path, mask):
    """경로가 전선 색 밖을 지나는 구간(틈). (길이 px, 가운데 u, v)를 긴 순서로."""
    on = mask[path[:, 1], path[:, 0]]
    out, i = [], 0
    while i < len(path):
        if on[i]:
            i += 1
            continue
        j = i
        while j + 1 < len(path) and not on[j + 1]:
            j += 1
        p, q = path[max(i - 1, 0)], path[min(j + 1, len(path) - 1)]   # 틈 앞뒤의 전선 픽셀
        mid = path[(i + j) // 2]
        out.append((float(np.hypot(*(q - p))), int(mid[0]), int(mid[1])))
        i = j + 1
    return sorted(out, reverse=True)


def cmd_route(a):
    """경유점 사이를 전선 색 픽셀 위의 최소 비용 경로로 잇는다.

    비용은 전선 색 픽셀 안에서 1 + 3/(획 가장자리까지 거리)라 획 가운데를 따라가고, 밖에서는 --gap-cost라
    강·글자로 끊긴 짧은 틈은 건너가되 먼 길로 돌아가지 않는다. 같은 색이라도 막다른 가지(군단 경계 등)는
    경유점 사이 경로에 들어가지 않는다. 날짜가 다른 선이 같은 색으로 붙어 있으면 경유점을 더 찍거나
    join=line(손으로 읽은 직선 구간)을 쓴다.
    """
    from scipy.ndimage import distance_transform_edt
    from skimage.graph import route_through_array
    from skimage.measure import approximate_polygon
    img = np.asarray(load_rgb(a.image))
    h, w = img.shape[:2]
    mask = line_mask(img, [np.array([int(c) for c in s.split(',')]) for s in a.line_rgb], a.rgb_tol)
    if not mask.any():
        sys.exit('전선 색 픽셀이 하나도 없다. --line-rgb와 --rgb-tol을 확인할 것.')
    cost = np.where(mask, 1.0 + 3.0 / np.maximum(distance_transform_edt(mask), 1.0), a.gap_cost)
    for box in a.block or []:
        x0, y0, x1, y1 = map(int, box.split(','))
        cost[max(y0, 0):y1, max(x0, 0):x1] = np.inf          # 지나갈 수 없음 (범례, 다른 날짜 선 등)
    to_line = distance_transform_edt(~mask)                  # 각 픽셀에서 가장 가까운 전선 색 픽셀까지 거리
    with open(a.waypoints, encoding='utf-8') as f:
        wps = [(float(r['u']), float(r['v']), (r.get('join') or 'route').strip()) for r in csv.DictReader(f)]
    if len(wps) < 2:
        sys.exit('경유점이 2개 이상 있어야 한다.')
    kpp = read_json(a.fit)['km_per_px'] if a.fit else None

    def size(px):
        return f'{px:.0f}px' + (f' ({px * kpp:.2f}km)' if kpp else '')
    idx = []
    for n, (u, v, join) in enumerate(wps, 1):
        if join not in ('route', 'line'):
            sys.exit(f'경유점 {n}: join은 route 또는 line이다 ({join!r}).')
        if not (0 <= u < w and 0 <= v < h):
            sys.exit(f'경유점 {n} ({u}, {v})이 이미지 밖이다 ({w}x{h}).')
        iu, iv = int(u), int(v)
        if not np.isfinite(cost[iv, iu]):
            sys.exit(f'경유점 {n} ({u}, {v})이 --block 상자 안에 있다.')
        idx.append((iu, iv))
        d = float(to_line[iv, iu])
        note = '' if d <= 3 or n in (1, len(wps)) else '  ← 전선 색에서 떨어져 있다. 위치나 색을 확인할 것'
        print(f'경유점 {n} ({u:g}, {v:g}): 가장 가까운 전선 색까지 {size(d)}{note}')
    verts, all_gaps = [wps[0][:2]], []
    for n in range(1, len(wps)):
        (u0, v0, _), (u1, v1, join) = wps[n - 1], wps[n]
        if join == 'line':
            verts.append((u1, v1))
            print(f'구간 {n}: 직선 (손으로 읽음)')
            continue
        (iu0, iv0), (iu1, iv1) = idx[n - 1], idx[n]
        try:
            rc, _ = route_through_array(cost, (iv0, iu0), (iv1, iu1), fully_connected=True, geometric=True)
        except ValueError:
            sys.exit(f'구간 {n}: 경로를 찾지 못했다 (--block 상자가 길을 막았는지 확인할 것).')
        path = np.array([(c, r) for r, c in rc])
        pts = path + 0.5                                      # 픽셀 번호 → 픽셀 가운데 (sheet 격자와 같은 좌표)
        pts[0], pts[-1] = (u0, v0), (u1, v1)                  # 경유점은 읽은 값 그대로 둔다
        pts = approximate_polygon(pts, tolerance=a.simplify)
        verts.extend(map(tuple, pts[1:]))
        gaps = gaps_on(path, mask)
        all_gaps += gaps
        on = mask[path[:, 1], path[:, 0]].mean()
        longest = f', 가장 긴 틈 {size(gaps[0][0])} ({gaps[0][1]}, {gaps[0][2]})' if gaps else ''
        print(f'구간 {n}: 경로 {len(path)}px → 꼭짓점 {len(pts) - 1}개, 전선 색 위 {on:.0%}{longest}')
    with open(a.out, 'w', encoding='utf-8', newline='') as f:
        f.write('u,v\n' + ''.join(f'{u:.1f},{v:.1f}\n' for u, v in verts))
    print(f'{a.out}: 꼭짓점 {len(verts)}개')
    big = [g for g in all_gaps if g[0] > a.warn_gap]
    if big:
        print(f'{a.warn_gap:g}px 넘는 틈 {len(big)}곳 (--check 그림에서 확인할 것): ' +
              ', '.join(f'({u}, {v}) {size(L)}' for L, u, v in big[:10]))
    if a.check:                                               # 원본을 흐리게 깔고 전선 색 픽셀·경로·경유점·틈을 그린다
        base = Image.blend(Image.fromarray(img), Image.new('RGB', (w, h), 'white'), 0.6)
        arr = np.asarray(base).copy()
        arr[mask] = (0, 170, 255)
        im = Image.fromarray(arr)
        d = ImageDraw.Draw(im)
        d.line([tuple(p) for p in verts], fill=(255, 0, 200), width=2)
        for u, v, _ in wps:
            d.ellipse([u - 6, v - 6, u + 6, v + 6], outline=(0, 150, 0), width=2)
        for L, u, v in big:
            r = max(8, L / 2)
            d.ellipse([u - r, v - r, u + r, v + r], outline=(255, 120, 0), width=2)
        im.save(a.check)
        print(f'{a.check}: 하늘색 = 전선 색 픽셀, 분홍 = 경로, 초록 = 경유점, 주황 = {a.warn_gap:g}px 넘는 틈')


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
    lon, lat = proj_inv(proj_of(fit), *apply(H, uv[:, 0], uv[:, 1]))
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
        lon, lat = proj_inv(proj_of(fit), *apply(H, uv[:, 0], uv[:, 1]))
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


# ---------------------------------------------------------------- sides
FRAME = (110.0, 25.0, 150.0, 50.0)   # frontend/src/lib/geometry.ts의 FRAME (서, 남, 동, 북)


def screen_north(line):
    """화면(splitFrame)이 북측으로 칠하는 범위: 전선 양 끝을 틀 끝까지 정서·정동으로 늘린 선의 위쪽."""
    w, s, e, n = FRAME
    c = list(line.coords)
    return Polygon([(w, c[0][1])] + c + [(e, c[-1][1]), (e, n), (w, n)]).buffer(0)


def extend_ends(line, km):
    """끝점을 끝 구간 방향으로 km만큼 늘린다 (해안선을 확실히 넘어 육지를 자르도록)."""
    c = np.array(line.coords)

    def ray(p, toward):
        for q in toward:                                    # 1km 넘게 떨어진 첫 점으로 방향을 잡는다
            if math.hypot((p[0] - q[0]) * KM_LON, (p[1] - q[1]) * KM_LAT) > 1:
                break
        d = np.array([(p[0] - q[0]) * KM_LON, (p[1] - q[1]) * KM_LAT])
        d = d / np.linalg.norm(d) * km
        return (p[0] + d[0] / KM_LON, p[1] + d[1] / KM_LAT)
    return LineString([ray(c[0], c[1:])] + [tuple(p) for p in c] + [ray(c[-1], c[-2::-1])])


def start_inside(poly, edges):
    """고리의 첫 꼭짓점을 경계(전선·연장선)에서 가장 먼 꼭짓점으로 옮긴다.
    화면은 첫 꼭짓점으로 추가 영역이 어느 쪽 안에 있는지 판정하므로, 경계 위의 점으로 시작하면 판정이 흔들린다."""
    ring = list(poly.exterior.coords)[:-1]
    k = int(np.argmax([edges.distance(Point(p)) for p in ring]))
    ring = ring[k:] + ring[:k]
    return Polygon(ring + [ring[0]], [i.coords for i in poly.interiors])


def cmd_sides(a):
    """화면이 반대쪽 색으로 칠하는 육지를 찾아 추가 영역으로 만든다.

    화면은 전선 양 끝을 정서·정동으로 늘려 양측을 나누므로, 늘린 선이 지나가는 육지(서쪽 끝이 남해안일 때의
    남서 해안, 서해안 하구에서 끝날 때 하구 건너편 해안 등)가 반대쪽 색이 된다. 본토를 전선만으로 나눈 조각마다
    전선에 길게 맞닿은 쪽이 맞는 색이고, 늘린 선 때문에 떨어져 나간 부분이 잘못 칠해지는 곳이다.
    섬은 --islands-km 안에 있는 땅(본토 조각이나 이미 정한 섬)의 색을 따른다. 가까운 섬부터 섬에서 섬으로 이어 가며 정한다.
    그 거리 안에 양쪽 땅이 다 있으면(예: 낙동강 방어선 때 거제도) 정하지 않고 화면 그대로 두고,
    어느 땅에서도 그보다 먼 섬(예: 개전 때 백령도)도 화면 그대로 둔다. 사료로 확인되는 섬은 area로 따로 그린다.
    """
    from shapely.ops import split
    line = shape(read_json(a.line)['features'][0]['geometry'])
    land = load_outline(a.outline)
    parts = sorted(land.geoms, key=lambda p: p.area, reverse=True)
    main, islands = parts[0], parts[1:]
    north = screen_north(line)
    w, s, e, n = FRAME
    c = list(line.coords)
    edges = LineString([(w, c[0][1])] + c + [(e, c[-1][1])])
    cut = extend_ends(line, a.extend_km)
    touch = line.buffer(1e-5)

    def polys_of(g):                                         # 교차 결과에서 다각형만 (점·선 조각은 버린다)
        return [q for q in getattr(g, 'geoms', [g]) if q.geom_type == 'Polygon' and not q.is_empty]

    def contact(g):                                          # 전선에 맞닿은 길이
        return sum(q.boundary.intersection(touch).length for q in polys_of(g))

    def side_of(piece):                                      # 전선에 길게 맞닿은 쪽이 맞는 색
        pn, ps = piece.intersection(north), piece.difference(north)
        ln, ls = contact(pn), contact(ps)
        if ln == ls == 0:                                    # 전선에 닿지 않는 조각은 넓은 쪽을 따른다
            return 'north' if pn.area >= ps.area else 'south'
        return 'north' if ln >= ls else 'south'

    def km(g):                                               # 거리 계산용 국지 km 좌표
        import shapely
        return shapely.transform(g, lambda xy: np.column_stack(to_km(xy[:, 0], xy[:, 1])))

    pieces = [(p, side_of(p)) for p in split(main, cut).geoms if p.geom_type == 'Polygon']
    print(f'본토를 전선으로 나눈 조각 {len(pieces)}개')
    wrong = []                                               # (다각형, 맞는 색, 설명)
    for p, side in pieces:
        bad = p.difference(north) if side == 'north' else p.intersection(north)
        wrong += [(g, side, '본토') for g in polys_of(bad)]
    known = [(km(p), side) for p, side in pieces]            # 섬은 가까운 땅부터 차례로 정한다
    todo = [(isl, km(isl)) for isl in islands]
    unsure = 0
    while todo:
        best = None
        for i, (isl, k) in enumerate(todo):
            d = min(g.distance(k) for g, _ in known)
            if d <= a.islands_km and (best is None or d < best[0]):
                best = (d, i)
        if best is None:
            break
        d, i = best
        isl, k = todo.pop(i)
        sides = {side for g, side in known if g.distance(k) <= a.islands_km}
        if len(sides) > 1:                                   # 양쪽 땅이 다 가까우면 정하지 않고 화면 그대로 둔다
            unsure += 1
            print(f'  섬 {area_km2(isl):,.1f}km² ({isl.representative_point().x:.2f}, {isl.representative_point().y:.2f}): '
                  f'양쪽 땅이 {a.islands_km:g}km 안에 있어 화면 그대로')
            continue
        side = sides.pop()
        known.append((k, side))
        bad = isl.difference(north) if side == 'north' else isl.intersection(north)
        wrong += [(g, side, f'섬 {d:.0f}km') for g in polys_of(bad)]
    out = []
    for g, side, what in sorted(wrong, key=lambda t: -area_km2(t[0])):
        km2 = area_km2(g)
        if km2 < a.min_km2:
            continue
        g = Polygon(g.exterior.coords, [i.coords for i in g.interiors]).segmentize(0.01)
        g = Polygon([(round(x, 6), round(y, 6)) for x, y in g.exterior.coords],
                    [[(round(x, 6), round(y, 6)) for x, y in i.coords] for i in g.interiors])
        out.append(start_inside(g, edges))
        print(f'  {what}: {km2:,.1f}km² → {"북측" if side == "north" else "남측"} 색')
    print(f'보정 영역 {len(out)}개, 합계 {sum(area_km2(g) for g in out):,.1f}km² (작은 조각 {a.min_km2}km² 미만은 뺌)')
    polys = list(out)
    if a.areas:                                              # area로 그린 고립 지역·교두보와 합친다
        polys = [shape(f['geometry']) for f in read_json(a.areas)['features']] + polys
    union_area = area_km2(MultiPolygon(polys).buffer(0))
    if abs(union_area - sum(area_km2(p) for p in polys)) > 1:
        sys.exit('추가 영역끼리 겹친다. --areas의 다각형과 보정 영역을 확인할 것.')
    if not polys:
        print('잘못 칠해지는 육지가 없다. 추가 영역 파일을 만들지 않는다.')
        return
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
        xs, ys = proj_fwd(proj_of(fit), *np.array(line.coords).T)
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
    # Flyway처럼 V<버전>__<설명>.sql을 버전 순서대로 모두 적용한다 (V2가 V1에 열을 더하는 식이라 하나만 적용하면 표가 다르다)
    def version(name):
        return tuple(int(x) for x in re.split(r'[._]', name[1:].split('__')[0]))
    migrations = sorted((f for f in os.listdir(a.migrations) if re.match(r'V\d+([._]\d+)*__.+\.sql$', f)), key=version)
    for name in migrations:
        r = psql('-d', a.db, '-f', os.path.join(a.migrations, name))
        if r.returncode:
            sys.exit(f'마이그레이션 실패 ({name}): ' + r.stderr)
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
        sql = (f"INSERT INTO frontline_snapshot (snapshot_date, title, front_line, extra_areas, source_map, note) VALUES "
               f"(:'d'::date, :'t', ST_GeomFromGeoJSON(:'g'), {areas}, :'s', NULLIF(:'n',''))")
        r = psql('-d', a.db, '-v', f'd={d}', '-v', f"t={row.get('title') or ''}", '-v', f'g={geom}',
                 '-v', f"s={row['source_map']}", '-v', f"n={row['note']}", input=sql)
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
    s.add_argument('--proj', choices=['tm', 'equirect'], default='tm',
                   help='경위도를 바꿀 평면: tm(기준점 가운데 중심 횡메르카토르, 기본) 또는 equirect(38°N 고정 환산, 예전 방식)')
    s.add_argument('--tol-km', type=float, default=1.5); s.add_argument('--max-km-per-px', type=float, default=0.5)
    s.add_argument('--outline', default=DEFAULT_OUTLINE)
    s.add_argument('--sea-rgb', help='해안선 검사용 바다색 R,G,B'); s.add_argument('--sea-tol', type=int, default=40)
    s.add_argument('--exclude', nargs='*', help='해안선 검사에서 뺄 상자 x0,y0,x1,y1 (범례 등)')
    s = sub.add_parser('route'); s.add_argument('image')
    s.add_argument('waypoints', help='CSV: u,v[,join] 서→동 순서. join=route(기본, 전선 색을 따라감) 또는 line(앞 점과 직선)')
    s.add_argument('out', help='꼭짓점 CSV (u,v) → trace에 넣는다')
    s.add_argument('--line-rgb', nargs='+', required=True, help='전선 색 R,G,B (여러 개 주면 그중 하나에 가까우면 전선)')
    s.add_argument('--rgb-tol', type=int, default=60, help='R·G·B 차이 합의 허용치')
    s.add_argument('--gap-cost', type=float, default=30.0, help='전선 색 밖 한 픽셀의 비용 (안은 1~4)')
    s.add_argument('--simplify', type=float, default=0.8, help='꼭짓점 줄이기 허용 오차 px')
    s.add_argument('--block', nargs='*', help='지나가지 못하게 할 상자 x0,y0,x1,y1 (범례, 다른 날짜의 선 등)')
    s.add_argument('--warn-gap', type=float, default=15.0, help='이 길이(px)를 넘는 틈은 따로 알린다')
    s.add_argument('--fit', help='fit 결과 JSON (길이를 km로도 보여 준다)')
    s.add_argument('--check', help='확인 그림 PNG')
    s = sub.add_parser('trace'); s.add_argument('image'); s.add_argument('fit'); s.add_argument('vertices', help='CSV: u,v (서→동 순서 권장)')
    s.add_argument('out', help='<날짜>.front_line.geojson'); s.add_argument('--outline', default=DEFAULT_OUTLINE)
    s.add_argument('--snap-km', type=float, default=3.0); s.add_argument('--snap-rgb', help='전선 색 R,G,B')
    s.add_argument('--snap-tol', type=int, default=60); s.add_argument('--snap-radius', type=int, default=4)
    s = sub.add_parser('area'); s.add_argument('fit'); s.add_argument('vertices', help='CSV: part,u,v (part마다 고리 하나)')
    s.add_argument('out', help='<날짜>.extra_areas.geojson'); s.add_argument('--outline', default=DEFAULT_OUTLINE)
    s = sub.add_parser('sides'); s.add_argument('line', help='<날짜>.front_line.geojson')
    s.add_argument('out', help='<날짜>.extra_areas.geojson (보정 영역 + --areas)')
    s.add_argument('--areas', help='area로 만든 고립 지역·교두보 파일 (함께 넣는다)')
    s.add_argument('--islands-km', type=float, default=10.0, help='이 거리(km) 안의 섬은 가장 가까운 땅의 색을 따른다')
    s.add_argument('--min-km2', type=float, default=0.5, help='이보다 작은 조각은 뺀다')
    s.add_argument('--extend-km', type=float, default=2.0, help='본토를 자를 때 전선 끝을 늘리는 길이')
    s.add_argument('--outline', default=DEFAULT_OUTLINE)
    s = sub.add_parser('preview'); s.add_argument('line'); s.add_argument('prefix')
    s.add_argument('--image'); s.add_argument('--fit'); s.add_argument('--previous', help='직전 스냅샷 front_line 파일')
    s.add_argument('--areas', help='같은 날짜의 extra_areas 파일')
    s.add_argument('--outline', default=DEFAULT_OUTLINE)
    s = sub.add_parser('validate'); s.add_argument('--dir', default='data/frontlines')
    s.add_argument('--migrations', default='backend/src/main/resources/db/migration', help='Flyway 마이그레이션 폴더 (V*.sql을 모두 적용)')
    s.add_argument('--outline', default=DEFAULT_OUTLINE); s.add_argument('--db', default='frontline_check')
    s.add_argument('--end-m', type=float, default=100.0)
    a = p.parse_args()
    {'sheet': cmd_sheet, 'fit': cmd_fit, 'route': cmd_route, 'trace': cmd_trace, 'area': cmd_area, 'sides': cmd_sides, 'preview': cmd_preview, 'validate': cmd_validate}[a.cmd](a)


if __name__ == '__main__':
    main()
