import type { FeatureCollection } from "geojson";
import type { ExpressionSpecification, LngLatBoundsLike, StyleSpecification } from "maplibre-gl";
import { FRAME, extraAreaSide, splitFrame, type Side } from "./geometry";
import type { Position, Snapshot } from "./snapshots";

// 폰 화면 시안의 색. 북측 빨강, 남측 파랑은 군사 지도의 관례를 따른다 (기획서 6장)
export const COLORS = {
  sea: "#e9edf1",
  neighbor: "#cdd3da",
  coast: "#8e98a3",
  north: "#db8580",
  south: "#7da3ce",
  frontLine: "#1a1d21",
  // 전투 재생의 공격·이동 화살표 (진영색보다 진하게, docs/battle-replay.md 1장)
  arrowUn: "#2f5f9e",
  arrowCommunist: "#a8403a",
  // 전선을 감춘 전투 재생에서 한반도 땅 색 (바다·주변 나라와 구분되는 옅은 회색)
  landNeutral: "#d9dde2",
} as const;

// data/reference/korea_outline.geojson의 범위. 처음 화면을 한반도 전체에 맞춘다
export const KOREA_BOUNDS: LngLatBoundsLike = [
  [124.211316, 33.197577],
  [131.862522, 43.01027],
];

// 가장 많이 축소했을 때 보이는 범위: 한반도와 둘레(중국 동북부·산둥 반도, 일본 서쪽 해안).
// 화면 크기마다 이 범위가 딱 들어오는 줌을 계산해 최소 줌으로 쓴다 (FrontlineMap)
export const VIEW_LIMIT_BOUNDS: [[number, number], [number, number]] = [
  [118.5, 30],
  [137, 45],
];

// 화면이 틀(FRAME) 밖을 보여 주지 않도록 틀보다 조금 안쪽으로 묶는다. 끌 수 있는 범위는 이 안에서 정한다
export const SAFE_BOUNDS = {
  west: FRAME.west + 2,
  south: FRAME.south + 1,
  east: FRAME.east - 2,
  north: FRAME.north - 1,
} as const;

const EMPTY: FeatureCollection = { type: "FeatureCollection", features: [] };

const sideColor: ExpressionSpecification = ["match", ["get", "side"], "north", COLORS.north, COLORS.south];
// 전투 재생에서 감췄던 양측 색을 되돌릴 때 쓴다
export const SIDE_COLOR = sideColor;
const arrowColor: ExpressionSpecification = ["match", ["get", "side"], "un", COLORS.arrowUn, COLORS.arrowCommunist];

// 아래에서 위로 쌓는다: 바다 → 양측 색 → 추가 영역 → 바다 덮개 → 주변 나라 → 해안선 → 전선
export const MAP_STYLE: StyleSpecification = {
  version: 8,
  sources: {
    // 양측 다각형과 전선을 한 소스에 둔다. 이동하는 동안 둘이 같은 순간의 모양으로 그려진다
    war: { type: "geojson", data: EMPTY },
    extra: { type: "geojson", data: EMPTY },
    // 전투 재생 중에만 채운다
    arrows: { type: "geojson", data: EMPTY },
    seaMask: { type: "geojson", data: "/geo/sea_mask.geojson" },
    neighbors: { type: "geojson", data: "/geo/neighbors.geojson" },
  },
  layers: [
    { id: "sea", type: "background", paint: { "background-color": COLORS.sea } },
    {
      id: "sides",
      type: "fill",
      source: "war",
      filter: ["==", ["geometry-type"], "Polygon"],
      paint: { "fill-color": sideColor },
    },
    { id: "extra", type: "fill", source: "extra", paint: { "fill-color": sideColor } },
    { id: "sea-mask", type: "fill", source: "seaMask", paint: { "fill-color": COLORS.sea } },
    { id: "neighbors", type: "fill", source: "neighbors", paint: { "fill-color": COLORS.neighbor } },
    { id: "coast", type: "line", source: "seaMask", paint: { "line-color": COLORS.coast, "line-width": 0.6 } },
    {
      id: "front-line",
      type: "line",
      source: "war",
      filter: ["==", ["geometry-type"], "LineString"],
      layout: { "line-cap": "round", "line-join": "round" },
      paint: { "line-color": COLORS.frontLine, "line-width": 2.6 },
    },
    {
      id: "front-ends",
      type: "circle",
      source: "war",
      filter: ["==", ["geometry-type"], "Point"],
      paint: { "circle-color": COLORS.frontLine, "circle-radius": 3 },
    },
    // 화살표: 흰 테두리(선·촉) 위에 진영색 선과 촉을 얹는다
    {
      id: "arrow-casing",
      type: "line",
      source: "arrows",
      filter: ["==", ["geometry-type"], "LineString"],
      layout: { "line-cap": "round", "line-join": "round" },
      paint: { "line-color": "#ffffff", "line-width": 9, "line-opacity": ["get", "opacity"] },
    },
    {
      id: "arrow-head-casing",
      type: "line",
      source: "arrows",
      filter: ["==", ["geometry-type"], "Polygon"],
      layout: { "line-join": "round" },
      paint: { "line-color": "#ffffff", "line-width": 4, "line-opacity": ["get", "opacity"] },
    },
    {
      id: "arrow-line",
      type: "line",
      source: "arrows",
      filter: ["==", ["geometry-type"], "LineString"],
      layout: { "line-cap": "round", "line-join": "round" },
      paint: { "line-color": arrowColor, "line-width": 5, "line-opacity": ["get", "opacity"] },
    },
    {
      id: "arrow-head",
      type: "fill",
      source: "arrows",
      filter: ["==", ["geometry-type"], "Polygon"],
      paint: { "fill-color": arrowColor, "fill-opacity": ["get", "opacity"] },
    },
  ],
};

// 전투 재생의 화살표: 선(촉 밑동까지)과 삼각형 촉. size는 촉 길이(경도 단위)로, view 폭에 맞춰 정한다.
// 위도가 올라가면 경도 1도가 짧아지므로 남북 방향은 cos(위도)로 맞춰 촉이 찌그러지지 않게 한다
export function arrowData(
  arrows: { side: string; path: Position[]; opacity: number }[],
  size: number,
): FeatureCollection {
  const features: FeatureCollection["features"] = [];
  for (const arrow of arrows) {
    if (arrow.path.length < 2 || arrow.opacity <= 0) continue;
    const tip = arrow.path[arrow.path.length - 1];
    const prev = arrow.path[arrow.path.length - 2];
    const k = Math.cos((tip[1] * Math.PI) / 180);
    // 평면(경도, 위도/k)에서 방향을 잡는다
    const dx = tip[0] - prev[0];
    const dy = (tip[1] - prev[1]) / k;
    const len = Math.sqrt(dx * dx + dy * dy);
    if (len === 0) continue;
    const ux = dx / len;
    const uy = dy / len;
    const head = Math.min(size, len * 0.9 + size * 0.1);
    const base: Position = [tip[0] - ux * head, tip[1] - uy * head * k];
    const half = head * 0.5;
    const left: Position = [base[0] - uy * half, base[1] + ux * half * k];
    const right: Position = [base[0] + uy * half, base[1] - ux * half * k];
    const properties = { side: arrow.side, opacity: arrow.opacity };
    features.push({
      type: "Feature",
      properties,
      geometry: { type: "LineString", coordinates: [...arrow.path.slice(0, -1), base] },
    });
    features.push({
      type: "Feature",
      properties,
      geometry: { type: "Polygon", coordinates: [[tip, left, right, tip]] },
    });
  }
  return { type: "FeatureCollection", features };
}

// 전선 하나로 양측 다각형, 전선, 양 끝점을 만든다
export function warData(line: Position[]): FeatureCollection {
  const rings = splitFrame(line);
  const sides = (["north", "south"] as Side[]).map((side) => ({
    type: "Feature" as const,
    properties: { side },
    geometry: { type: "Polygon" as const, coordinates: [rings[side]] },
  }));
  const ends = [line[0], line[line.length - 1]].map((point) => ({
    type: "Feature" as const,
    properties: {},
    geometry: { type: "Point" as const, coordinates: point },
  }));
  return {
    type: "FeatureCollection",
    features: [
      ...sides,
      { type: "Feature", properties: {}, geometry: { type: "LineString", coordinates: line } },
      ...ends,
    ],
  };
}

// 추가 영역마다 어느 쪽이 차지했는지 붙인다
export function extraData(snapshot: Snapshot): FeatureCollection {
  const line = snapshot.frontLine.coordinates;
  return {
    type: "FeatureCollection",
    features: (snapshot.extraAreas?.coordinates ?? []).map((polygon) => ({
      type: "Feature",
      properties: { side: extraAreaSide(polygon, line) },
      geometry: { type: "Polygon", coordinates: polygon },
    })),
  };
}

export const EMPTY_DATA = EMPTY;
