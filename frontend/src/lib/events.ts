// GET /api/events 응답 (백엔드 EventResponse와 같은 모양)
import { formatDate } from "./snapshots";

export type EventSource = {
  id: string;
  title: string;
  author: string | null;
  url: string | null;
  license: string;
};

export type Evidence = {
  locator: string; // 장 이름이나 쪽수
  quote: string | null; // 원문은 퍼블릭 도메인 자료만 싣는다 (기획서 7장)
  source: EventSource;
};

export type Claim = { text: string; evidence: Evidence[] };

export type WarEvent = {
  id: string;
  title: string;
  date: string; // YYYY-MM-DD
  datePrecision: "day" | "month";
  placeName: string;
  lon: number;
  lat: number;
  category: string;
  hook: string;
  body: string;
  status: "draft" | "reviewed"; // 화면에는 보이지 않는다. 백엔드가 reviewed만 내보내는 데 쓴다
  claims: Claim[] | null;
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
