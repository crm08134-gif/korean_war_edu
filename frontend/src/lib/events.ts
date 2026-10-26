// GET /api/events 응답 (백엔드 EventResponse와 같은 모양)
import { formatDate } from "./snapshots";

// 사건 종류. 지도 마커 아이콘을 고른다 (CLAUDE.md 4장)
export type EventKind = "battle" | "offensive" | "capture" | "landing" | "withdrawal" | "talks";

// 본문 블록: 소제목(배경·경과·결과·의미), 문단, 표
export type TableBlock = {
  type: "table";
  caption: string | null;
  columns: string[];
  rows: string[][]; // 줄마다 칸 수가 columns와 같다 (적재기가 검사)
  note: string | null; // 표 아래 작은 글씨
};

export type Block = { type: "heading"; text: string } | { type: "paragraph"; text: string } | TableBlock;

// 카드 맨 아래 출처 목록 한 줄: "미 육군 군사사 센터, 책 제목, 3장"
export type SourceRef = {
  publisher: string;
  title: string;
  url: string | null;
  locator: string; // 장·쪽
};

export type WarEvent = {
  id: string;
  title: string;
  date: string; // YYYY-MM-DD
  datePrecision: "day" | "month";
  placeName: string;
  lon: number;
  lat: number;
  kind: EventKind;
  summary: string; // 2~3문장
  body: Block[];
  sources: SourceRef[];
  status: "draft" | "reviewed"; // 화면에는 보이지 않는다. 백엔드가 reviewed만 내보내는 데 쓴다
};

export async function fetchEvents(signal?: AbortSignal): Promise<WarEvent[]> {
  const res = await fetch("/api/events", { signal });
  if (!res.ok) {
    throw new Error(`GET /api/events ${res.status}`);
  }
  return res.json();
}

// 직전 스냅샷 다음 날부터 지금 스냅샷 날짜까지의 사건. 첫 스냅샷(from 없음)은 그날까지
export function eventsBetween(events: WarEvent[], from: string | null, to: string): WarEvent[] {
  return events.filter((event) => event.date <= to && (from === null || event.date > from));
}

// 날짜 정밀도에 맞춘 표기: "1950. 6. 25." 또는 "1950년 8월"
export function formatEventDate(event: WarEvent): string {
  if (event.datePrecision === "month") {
    const [y, m] = event.date.split("-").map(Number);
    return `${y}년 ${m}월`;
  }
  return formatDate(event.date);
}
