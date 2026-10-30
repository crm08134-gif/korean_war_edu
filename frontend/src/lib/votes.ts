// 관련 인물 호감도(좋아요·싫어요). 백엔드 VoteController와 같은 모양
// 회원 기능이 생기기 전이라, 브라우저마다 익명 ID를 하나 만들어 X-Voter-Id 머리글로 보낸다

export type VoteValue = 1 | -1 | 0; // 1 좋아요, -1 싫어요, 0 취소

export type VoteCount = {
  personId: string;
  up: number;
  down: number;
  mine: VoteValue; // 이 기기가 누른 표. 누르지 않았으면 0
};

const VOTER_KEY = "korean-war-voter-id";
let memoryVoterId: string | null = null;

// crypto.randomUUID는 HTTPS나 localhost에서만 있어서, 어디서나 되는 getRandomValues로 만든다
function newUuid(): string {
  const b = crypto.getRandomValues(new Uint8Array(16));
  b[6] = (b[6] & 0x0f) | 0x40; // 버전 4
  b[8] = (b[8] & 0x3f) | 0x80; // RFC 4122 변형
  const h = Array.from(b, (x) => x.toString(16).padStart(2, "0")).join("");
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

// 처음 한 번 만들어 브라우저에 남긴다. 저장소를 못 쓰는 창(사생활 보호 모드 등)에서는 이번 방문 동안만 쓴다
export function voterId(): string {
  try {
    const saved = localStorage.getItem(VOTER_KEY);
    if (saved) return saved;
    const id = newUuid();
    localStorage.setItem(VOTER_KEY, id);
    return id;
  } catch {
    memoryVoterId ??= newUuid();
    return memoryVoterId;
  }
}

export async function fetchVotes(eventId: string, signal?: AbortSignal): Promise<VoteCount[]> {
  const res = await fetch(`/api/votes?eventId=${encodeURIComponent(eventId)}`, {
    headers: { "X-Voter-Id": voterId() },
    signal,
  });
  if (!res.ok) throw new Error(`GET /api/votes ${res.status}`);
  return res.json();
}

// 표를 넣거나 바꾸거나(1, -1) 취소한다(0). 그 사건의 인물별 새 수를 돌려받는다
export async function putVote(eventId: string, personId: string, value: VoteValue): Promise<VoteCount[]> {
  const res = await fetch("/api/votes", {
    method: "PUT",
    headers: { "Content-Type": "application/json", "X-Voter-Id": voterId() },
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
