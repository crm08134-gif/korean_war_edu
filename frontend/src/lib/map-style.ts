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
} as const;

// data/reference/korea_outline.geojson의 범위. 처음 화면을 한반도 전체에 맞춘다
export const KOREA_BOUNDS: LngLatBoundsLike = [
  [124.211316, 33.197577],
  [131.862522, 43.01027],
];

// 화면이 틀(FRAME) 밖을 보여 주지 않도록 틀보다 조금 안쪽으로 묶는다
export const MAX_BOUNDS: LngLatBoundsLike = [
  [FRAME.west + 2, FRAME.south + 1],
  [FRAME.east - 2, FRAME.north - 1],
];

const EMPTY: FeatureCollection = { type: "FeatureCollection", features: [] };

const sideColor: ExpressionSpecification = ["match", ["get", "side"], "north", COLORS.north, COLORS.south];

// 아래에서 위로 쌓는다: 바다 → 양측 색 → 추가 영역 → 바다 덮개 → 주변 나라 → 해안선 → 전선
export const MAP_STYLE: StyleSpecification = {
  version: 8,
  sources: {
    // 양측 다각형과 전선을 한 소스에 둔다. 이동하는 동안 둘이 같은 순간의 모양으로 그려진다
    war: { type: "geojson", data: EMPTY },
    extra: { type: "geojson", data: EMPTY },
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
  ],
};

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
