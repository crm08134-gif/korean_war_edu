// 관련 인물 화면에 쓰는 이름표와 정렬
import { COLORS } from "./map-style";
import type { EventPerson } from "./events";

// 당시 국기 이름. 그림은 public/flags/<코드>.svg (1950년대 국기)
export const COUNTRY_LABEL: Record<string, string> = {
  kr: "대한민국",
  kp: "조선민주주의인민공화국",
  us: "미국",
  su: "소련",
  cn: "중화인민공화국",
  gb: "영국",
  fr: "프랑스",
  au: "오스트레일리아",
};

export const SIDE_LABEL: Record<EventPerson["side"], string> = {
  un: "유엔군 측",
  communist: "공산군 측",
};

// 지도 색과 같다: 북측 빨강, 남측 파랑
export const SIDE_COLOR: Record<EventPerson["side"], string> = {
  un: COLORS.south,
  communist: COLORS.north,
};

// 유엔·한국 측을 먼저, 공산 측을 나중에. 같은 진영 안에서는 사건 파일에 적은 순서
export function sortPeople(people: EventPerson[]): EventPerson[] {
  return [...people.filter((p) => p.side === "un"), ...people.filter((p) => p.side === "communist")];
}
