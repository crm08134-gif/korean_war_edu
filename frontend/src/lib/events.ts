// GET /api/events 응답 (백엔드 EventResponse와 같은 모양)
import { WAR_START, formatDate } from "./snapshots";

// 사건 종류. 지도 마커 아이콘을 고른다 (CLAUDE.md 4장)
export type EventKind = "battle" | "offensive" | "capture" | "landing" | "withdrawal" | "talks" | "aid" | "policy";

// 지도 밖 사건을 놓는 지도 가장자리: north 소련(위), east 미국(오른쪽), west 중국(왼쪽)
export type Region = "north" | "east" | "west";

// 본문 블록: 소제목(배경·경과·결과·의미), 문단, 표
export type TableBlock = {
  type: "table";
  caption: string | null;
  columns: string[];
  rows: string[][]; // 줄마다 칸 수가 columns와 같다 (적재기가 검사)
  note: string | null; // 표 아래 작은 글씨
};

// 본문 사진. file은 data/photos/의 파일 이름이고 /photos/<file>로 내려받는다
export type PhotoBlock = { type: "photo"; file: string; caption: string };

export type Block = { type: "heading"; text: string } | { type: "paragraph"; text: string } | TableBlock | PhotoBlock;

// 허용 목록의 라이선스 (CLAUDE.md 0장). 백엔드 DB가 이 밖의 값을 막는다
export const LICENSE_LABEL: Record<string, string> = {
  "public-domain": "퍼블릭 도메인",
  cc0: "CC0",
  "cc-by-2.0": "CC BY 2.0",
  "cc-by-2.5": "CC BY 2.5",
  "cc-by-3.0": "CC BY 3.0",
  "cc-by-4.0": "CC BY 4.0",
  "cc-by-sa-2.0": "CC BY-SA 2.0",
  "cc-by-sa-2.5": "CC BY-SA 2.5",
  "cc-by-sa-3.0": "CC BY-SA 3.0",
  "cc-by-sa-4.0": "CC BY-SA 4.0",
  "kogl-1": "공공누리 제1유형",
};

// 사진 메타데이터 (data/photos/photos.csv). 사진 아래 출처 표기에 쓴다
export type PhotoMeta = {
  file: string;
  title: string;
  author: string; // 작성자·기관 (예: 미 육군 통신대 / NARA)
  sourceUrl: string; // 원본 페이지
  license: string;
  licenseUrl: string | null;
  modified: string | null; // 크기 조정·자르기
};

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
  date: string; // YYYY-MM-DD. 어느 스냅샷에 보일지는 이 시작일로 정한다
  endDate: string | null; // 며칠 이어진 사건의 마지막 날
  datePrecision: "day" | "month";
  placeName: string;
  // 좌표와 region 중 하나만 있다 (DB가 막는다)
  lon: number | null;
  lat: number | null;
  region: Region | null;
  kind: EventKind;
  summary: string; // 2~3문장
  photoFile: string | null; // 대표 사진 (카드 위쪽)
  photoCaption: string | null;
  body: Block[];
  sources: SourceRef[];
  photos: PhotoMeta[]; // 대표 사진과 본문 사진의 메타데이터
  status: "draft" | "reviewed"; // 화면에는 보이지 않는다. 백엔드가 reviewed만 내보내는 데 쓴다
};

export async function fetchEvents(signal?: AbortSignal): Promise<WarEvent[]> {
  const res = await fetch("/api/events", { signal });
  if (!res.ok) {
    throw new Error(`GET /api/events ${res.status}`);
  }
  return res.json();
}

// 직전 스냅샷 다음 날부터 지금 스냅샷 날짜까지의 사건. 첫 스냅샷(from 없음)은 그날 사건만
// (그 앞의 사건은 "개전 전" 칸에 나온다)
export function eventsBetween(events: WarEvent[], from: string | null, to: string): WarEvent[] {
  return events.filter((event) => event.date <= to && (from === null ? event.date === to : event.date > from));
}

// "개전 전" 칸의 사건: 개전(1950-06-25)보다 앞선 사건 전부
export function prewarEvents(events: WarEvent[]): WarEvent[] {
  return events.filter((event) => event.date < WAR_START);
}

// 시작일만: "1950. 1. 12." 또는 "1949년 7월"
export function formatStartDate(event: WarEvent): string {
  if (event.datePrecision === "month") {
    const [y, m] = event.date.split("-").map(Number);
    return `${y}년 ${m}월`;
  }
  return formatDate(event.date);
}

// 날짜 정밀도에 맞춘 표기: "1950. 6. 25.", "1950년 8월", 기간이면 "1950. 8. 13.~8. 30."
export function formatEventDate(event: WarEvent): string {
  if (!event.endDate) {
    return formatStartDate(event);
  }
  // 끝나는 해가 같으면 끝 날짜의 연도는 뺀다
  const sameYear = event.endDate.slice(0, 4) === event.date.slice(0, 4);
  const [, m, d] = event.endDate.split("-").map(Number);
  return `${formatDate(event.date)}~${sameYear ? `${m}. ${d}.` : formatDate(event.endDate)}`;
}
