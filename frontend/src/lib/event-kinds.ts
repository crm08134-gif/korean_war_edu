import type { EventKind } from "./events";

// 사건 종류 이름 (CLAUDE.md 4장)
export const KIND_LABEL: Record<EventKind, string> = {
  battle: "전투",
  offensive: "공세·돌파",
  capture: "점령·함락·수복",
  landing: "상륙",
  withdrawal: "철수",
  talks: "회담·협정",
};

// 종류별 아이콘. 16×16 칸에 선(stroke)으로만 그린 직접 만든 도형이다 (남의 아이콘 세트를 쓰지 않는다)
// 지도 마커(HTML 글자)와 칩(React)이 같은 모양을 쓰도록 path 글자로 둔다
export const KIND_ICON: Record<EventKind, string[]> = {
  // 교차한 두 칼: 칼날, 손잡이 막대, 손잡이
  battle: ["M2 2l9 9", "M9.7 12.3l2.6-2.6", "M11 11l3 3", "M14 2l-9 9", "M6.3 12.3L3.7 9.7", "M5 11l-3 3"],
  // 굵은 화살표
  offensive: ["M2 8h11", "M9 4l4 4-4 4"],
  // 깃발: 깃대와 깃발
  capture: ["M4 14.5V1.5", "M4 2.5h8.5l-2 2.75 2 2.75H4"],
  // 닻: 고리, 몸통, 가로대, 양팔
  landing: ["M9.5 3a1.5 1.5 0 1 1-3 0 1.5 1.5 0 1 1 3 0", "M8 4.5v9.5", "M5 7h6", "M2.5 9.5c0 3 2.5 4.5 5.5 4.5s5.5-1.5 5.5-4.5"],
  // 되돌아가는 화살표(U자): 위로 갔다가 아래로 돌아온다
  withdrawal: ["M12 14V7a4 4 0 0 0-8 0v6", "M1.5 10.5L4 13l2.5-2.5"],
  // 접힌 문서와 글줄
  talks: ["M3.5 1.5h6l3 3v10h-9z", "M9.5 1.5v3h3", "M6 8h4.5", "M6 10.5h4.5"],
};

// 지도 마커처럼 HTML로 그릴 때 쓰는 SVG 글자. 값은 위의 상수뿐이라 그대로 넣어도 안전하다
export function kindIconSvg(kind: EventKind, size: number): string {
  const paths = KIND_ICON[kind].map((d) => `<path d="${d}"/>`).join("");
  return `<svg width="${size}" height="${size}" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths}</svg>`;
}
