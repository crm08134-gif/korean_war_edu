import type { Position } from "./snapshots";

// public/geo/sea_mask.geojson을 만든 틀과 같아야 한다 (data/reference/README.md)
export const FRAME = { west: 110, south: 25, east: 150, north: 50 } as const;

export type Side = "north" | "south";

// 전선(서→동) 양 끝을 틀 끝까지 정서·정동으로 늘려, 틀을 북측과 남측 두 다각형으로 나눈다.
// 육지 밖은 지도에서 바다 덮개와 주변 나라가 가리므로 결과적으로 한반도만 두 색으로 칠해진다.
export function splitFrame(line: Position[]): Record<Side, Position[]> {
  const first = line[0];
  const last = line[line.length - 1];
  const westEnd: Position = [FRAME.west, first[1]];
  const eastEnd: Position = [FRAME.east, last[1]];
  const path = [westEnd, ...line, eastEnd];
  return {
    north: [...path, [FRAME.east, FRAME.north], [FRAME.west, FRAME.north], westEnd],
    south: [...path, [FRAME.east, FRAME.south], [FRAME.west, FRAME.south], westEnd],
  };
}

// 두 전선의 i번째 점끼리 t(0~1)만큼 옮긴다. 점 개수가 다르면 도착 전선을 그대로 쓴다
export function interpolateLine(from: Position[], to: Position[], t: number): Position[] {
  if (from.length !== to.length) {
    return to;
  }
  return to.map(([x, y], i) => [from[i][0] + (x - from[i][0]) * t, from[i][1] + (y - from[i][1]) * t]);
}

// 천천히 출발해 천천히 멈춘다
export function easeInOutCubic(t: number): number {
  return t < 0.5 ? 4 * t * t * t : 1 - (-2 * t + 2) ** 3 / 2;
}

// 점이 고리(다각형 바깥 경계) 안에 있는지 (반직선 교차 개수로 판단)
export function isInsideRing([x, y]: Position, ring: Position[]): boolean {
  let inside = false;
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const [xi, yi] = ring[i];
    const [xj, yj] = ring[j];
    if (yi > y !== yj > y && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) {
      inside = !inside;
    }
  }
  return inside;
}

// 추가 영역(고립 지역·교두보)은 둘러싼 쪽의 반대편이 차지한 곳이다
export function extraAreaSide(polygon: Position[][], line: Position[]): Side {
  const surroundedByNorth = isInsideRing(polygon[0][0], splitFrame(line).north);
  return surroundedByNorth ? "south" : "north";
}
