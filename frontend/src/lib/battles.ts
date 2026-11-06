// GET /api/battles 응답 (백엔드 BattleResponse와 같은 모양)과 재생 계산.
// 설계와 데이터 규칙: docs/battle-replay.md
import type { Position, Snapshot } from "./snapshots";

export type BattleSide = "un" | "communist";

export type BattleUnit = {
  id: string;
  name: string;
  country: string;
  side: BattleSide;
};

export type BattleKeyframe = {
  date: string; // YYYY-MM-DD
  caption: string;
  front: Position[] | null; // 전투 지역의 전선 조각(서→동). 없으면 그날 스냅샷 전선 그대로
  positions: Record<string, Position>; // 그날 있는 부대만
};

export type Battle = {
  eventId: string;
  view: { west: number; south: number; east: number; north: number };
  maps: { title: string; note: string | null }[];
  units: BattleUnit[];
  keyframes: BattleKeyframe[];
};

export async function fetchBattles(signal?: AbortSignal): Promise<Battle[]> {
  const res = await fetch("/api/battles", { signal });
  if (!res.ok) {
    throw new Error(`GET /api/battles ${res.status}`);
  }
  return res.json();
}

// 키프레임 하나에서 다음 키프레임까지 걸리는 시간 (docs/battle-replay.md 1장)
export const SEGMENT_MS = 2500;

// 스냅샷 전선과 같은 점 개수. 그래야 키프레임 사이에서 i번째 점끼리 보간할 수 있다
const FRONT_POINTS = 500;

// 두 점 사이 거리의 제곱 (가장 가까운 점 찾기에만 쓰므로 경위도 그대로 비교한다)
function dist2(a: Position, b: Position): number {
  const dx = a[0] - b[0];
  const dy = a[1] - b[1];
  return dx * dx + dy * dy;
}

function nearestIndex(line: Position[], point: Position): number {
  let best = 0;
  for (let i = 1; i < line.length; i++) {
    if (dist2(line[i], point) < dist2(line[best], point)) best = i;
  }
  return best;
}

// 스냅샷 전선에서 조각 양 끝과 가장 가까운 두 점 사이를 조각으로 바꿔 끼운다.
// 순서가 뒤집히면(조각이 서→동이 아니면) 스냅샷 전선 그대로 쓴다
export function spliceFront(base: Position[], piece: Position[] | null): Position[] {
  if (!piece || piece.length < 2) return base;
  const start = nearestIndex(base, piece[0]);
  const end = nearestIndex(base, piece[piece.length - 1]);
  if (start >= end) {
    console.warn("전선 조각이 스냅샷 전선과 순서가 맞지 않아 스냅샷 전선을 그대로 쓴다");
    return base;
  }
  return [...base.slice(0, start), ...piece, ...base.slice(end + 1)];
}

// 선을 길이에 따라 같은 간격의 점 n개로 다시 찍는다
export function resample(line: Position[], n: number = FRONT_POINTS): Position[] {
  const lengths = [0];
  for (let i = 1; i < line.length; i++) {
    lengths.push(lengths[i - 1] + Math.sqrt(dist2(line[i - 1], line[i])));
  }
  const total = lengths[lengths.length - 1];
  if (total === 0) return Array.from({ length: n }, () => line[0]);
  const out: Position[] = [];
  let j = 1;
  for (let k = 0; k < n; k++) {
    const target = (total * k) / (n - 1);
    while (j < line.length - 1 && lengths[j] < target) j++;
    const span = lengths[j] - lengths[j - 1] || 1;
    const f = Math.min(1, Math.max(0, (target - lengths[j - 1]) / span));
    out.push([line[j - 1][0] + (line[j][0] - line[j - 1][0]) * f, line[j - 1][1] + (line[j][1] - line[j - 1][1]) * f]);
  }
  return out;
}

// 그날 이전(그날 포함)의 가장 늦은 스냅샷. 모두 늦으면 첫 스냅샷
function snapshotOn(snapshots: Snapshot[], date: string): Snapshot {
  let found = snapshots[0];
  for (const snapshot of snapshots) {
    if (snapshot.snapshotDate <= date) found = snapshot;
  }
  return found;
}

// 키프레임마다 앱 전체 전선(점 500개)을 미리 만든다. 재생 중에는 이 사이를 보간만 한다
export function prepareFronts(battle: Battle, snapshots: Snapshot[]): Position[][] {
  return battle.keyframes.map((k) =>
    resample(spliceFront(snapshotOn(snapshots, k.date).frontLine.coordinates, k.front)),
  );
}

export type UnitFrame = {
  unit: BattleUnit;
  at: Position;
  opacity: number; // 0이면 그날 없는 부대
  trail: Position[]; // 처음 나타난 곳 → 지금 자리
};

export type ReplayFrame = {
  front: Position[];
  units: UnitFrame[];
  nearest: number; // 지금 시각에 가장 가까운 키프레임
};

// 천천히 출발해 천천히 멈춘다 (데모와 같은 곡선)
function smooth(x: number): number {
  return x * x * (3 - 2 * x);
}

// 시각 t(0 ~ 키프레임 수 - 1)의 전선과 부대. t가 2.4면 셋째와 넷째 키프레임 사이 40% 지점
export function frameAt(battle: Battle, fronts: Position[][], t: number): ReplayFrame {
  const lastIndex = battle.keyframes.length - 1;
  const clamped = Math.min(lastIndex, Math.max(0, t));
  const seg = Math.min(lastIndex - 1, Math.floor(clamped));
  const f = smooth(clamped - seg);
  const a = battle.keyframes[seg];
  const b = battle.keyframes[seg + 1];

  const front = fronts[seg].map((p, i): Position => [
    p[0] + (fronts[seg + 1][i][0] - p[0]) * f,
    p[1] + (fronts[seg + 1][i][1] - p[1]) * f,
  ]);

  const units = battle.units.map((unit): UnitFrame => {
    const pa = a.positions[unit.id];
    const pb = b.positions[unit.id];
    let at: Position;
    let opacity: number;
    if (pa && pb) {
      at = [pa[0] + (pb[0] - pa[0]) * f, pa[1] + (pb[1] - pa[1]) * f];
      opacity = 1;
    } else if (pa) {
      at = pa; // 다음 날짜에 없는 부대는 제자리에서 흐려진다
      opacity = 1 - f;
    } else if (pb) {
      at = pb; // 다음 날짜에 처음 나오는 부대는 그 자리에서 나타난다
      opacity = f;
    } else {
      at = [0, 0];
      opacity = 0;
    }
    let start: Position | null = null;
    for (const k of battle.keyframes) {
      if (k.positions[unit.id]) {
        start = k.positions[unit.id];
        break;
      }
    }
    return {
      unit,
      at,
      opacity,
      trail: start && opacity > 0 ? [start, at] : [],
    };
  });

  return { front, units, nearest: Math.round(clamped) };
}

// 1950-08-13 → 1950. 8. 13.
export function formatKeyDate(iso: string): string {
  const [y, m, d] = iso.split("-").map(Number);
  return `${y}. ${m}. ${d}.`;
}
