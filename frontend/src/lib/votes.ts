// 관련 인물 호감도(좋아요·싫어요). 백엔드 VoteController와 같은 모양
// 숫자는 누구나 보고, 누르려면 로그인해야 한다 (2026-11-04 결정). 누가 눌렀는지는 로그인 쿠키(회원 번호)로 서버가 정한다

import { apiFetch } from "@/lib/api";

export type VoteValue = 1 | -1 | 0; // 1 좋아요, -1 싫어요, 0 취소

export type VoteCount = {
  personId: string;
  up: number;
  down: number;
  mine: VoteValue; // 내가 누른 표. 누르지 않았거나 로그인하지 않았으면 0
};

// 읽기는 공개다. 만료된 로그인 쿠키가 남아 있으면 401이 오는데, apiFetch가 갱신을 시도하고
// 그것도 실패하면 서버가 쿠키를 지운다. 그때는 비로그인으로 한 번 더 읽는다 (게시판과 같은 방식)
export async function fetchVotes(eventId: string, signal?: AbortSignal): Promise<VoteCount[]> {
  const path = `/api/votes?eventId=${encodeURIComponent(eventId)}`;
  let res = await apiFetch(path, { signal });
  if (res.status === 401) res = await fetch(path, { signal });
  if (!res.ok) throw new Error(`GET /api/votes ${res.status}`);
  return res.json();
}

// 표를 넣거나 바꾸거나(1, -1) 취소한다(0). 그 사건의 인물별 새 수를 돌려받는다
export async function putVote(eventId: string, personId: string, value: VoteValue): Promise<VoteCount[]> {
  // 쓰기 요청이라 CSRF 머리글이 필요하다 (apiFetch가 붙인다)
  const res = await apiFetch("/api/votes", {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ eventId, personId, value }),
  });
  if (!res.ok) throw new Error(`PUT /api/votes ${res.status}`);
  return res.json();
}

// 화면에서 먼저 바꿔 보여 줄 수: 같은 버튼을 다시 누르면 취소, 반대 버튼이면 옮긴다
export function applyVote(count: VoteCount, value: VoteValue): VoteCount {
  let { up, down } = count;
  if (count.mine === 1) up -= 1;
  if (count.mine === -1) down -= 1;
  if (value === 1) up += 1;
  if (value === -1) down += 1;
  return { ...count, up, down, mine: value };
}
