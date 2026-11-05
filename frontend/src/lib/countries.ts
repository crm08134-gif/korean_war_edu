// GET /api/countries 응답 (백엔드 CountryResponse와 같은 모양)
// 6·25 전쟁 참전국(전투병 파견 16개국)과 의료지원국(6개국). 숫자는 국가보훈부 표 그대로

export type Country = {
  id: string;
  name: string;
  group: "combat" | "medical";
  flag: string | null; // /flags/<flag>.svg. 아직 없으면 null (이름 첫 글자 원을 보인다)
  personnel: number | null; // 참전 연인원
  killed: number | null; // 전사자 (참전국만)
  casualties: number | null; // 전사·부상·실종·포로를 합친 수 (참전국만)
  unit: string | null; // 의료지원국의 지원 부대
  note: string | null;
};

// 화면 맨 아래 출처 목록 (data/events/sources.csv의 mpva-un-participation, mpva-un-casualties, unc-germany)
export const COUNTRY_SOURCES = [
  "국가보훈부, Size of War Participation (원자료: 박동찬, 『통계로 본 6·25전쟁』, 국방부 군사편찬연구소, 2014)",
  "국가보훈부, Casualties",
  "유엔군사령부, Germany (UNC Contributors) — 독일 적십자 병원의 운영 기간",
];

export async function fetchCountries(signal?: AbortSignal): Promise<Country[]> {
  const res = await fetch("/api/countries", { signal });
  if (!res.ok) {
    throw new Error(`GET /api/countries ${res.status}`);
  }
  return res.json();
}

// 1789000 → "1,789,000"
export function formatCount(value: number): string {
  return value.toLocaleString("ko-KR");
}
