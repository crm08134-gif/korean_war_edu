// GET /api/snapshots 응답 (백엔드 SnapshotResponse와 같은 모양)

export type Position = [number, number];

export type Snapshot = {
  snapshotDate: string; // YYYY-MM-DD
  sourceMap: string;
  note: string | null;
  // 모든 스냅샷이 같은 점 개수(500), 서→동 순서다. 프론트는 i번째 점끼리 보간한다
  frontLine: { type: "LineString"; coordinates: Position[] };
  extraAreas: { type: "MultiPolygon"; coordinates: Position[][][] } | null;
};

export async function fetchSnapshots(signal?: AbortSignal): Promise<Snapshot[]> {
  const res = await fetch("/api/snapshots", { signal });
  if (!res.ok) {
    throw new Error(`GET /api/snapshots ${res.status}`);
  }
  return res.json();
}

// 전쟁 기간: 개전과 정전협정 날짜
export const WAR_START = "1950-06-25";
export const WAR_END = "1953-07-27";

// 시간대와 상관없이 날짜만 비교하도록 UTC 자정으로 바꾼다
function toUtc(iso: string): number {
  const [y, m, d] = iso.split("-").map(Number);
  return Date.UTC(y, m - 1, d);
}

// 1950-06-25 → "1950. 6. 25."
export function formatDate(iso: string): string {
  const [y, m, d] = iso.split("-").map(Number);
  return `${y}. ${m}. ${d}.`;
}

// 전쟁 기간 전체에서 그 날짜가 놓이는 위치 (0~1)
export function positionInWar(iso: string): number {
  const start = toUtc(WAR_START);
  return (toUtc(iso) - start) / (toUtc(WAR_END) - start);
}
