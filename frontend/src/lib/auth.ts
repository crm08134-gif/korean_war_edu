// 로그인 상태와 닉네임. 백엔드 MeController·AuthController와 같은 모양
import { apiFetch } from "@/lib/api";

export type Me = {
  loggedIn: boolean;
  nickname: string | null;
  needsNickname: boolean;
  role: "USER" | "ADMIN" | null;
};

export const ANONYMOUS: Me = { loggedIn: false, nickname: null, needsNickname: false, role: null };

// 누르면 이 주소로 화면 전체가 넘어가 카카오·구글 로그인을 하고, 끝나면 앱 첫 화면으로 돌아온다
export const LOGIN_URL = {
  kakao: "/oauth2/authorization/kakao",
  google: "/oauth2/authorization/google",
} as const;

// 백엔드 NicknameRequest와 같은 규칙. 서버도 다시 검사한다
export const NICKNAME_PATTERN = /^[가-힣a-zA-Z0-9]{2,12}$/;
export const NICKNAME_RULE = "한글·영문·숫자 2~12자";

export async function fetchMe(signal?: AbortSignal): Promise<Me> {
  const res = await apiFetch("/api/me", { signal });
  // 갱신까지 실패했으면 로그인이 끊긴 것이다
  if (res.status === 401) return ANONYMOUS;
  if (!res.ok) throw new Error(`GET /api/me ${res.status}`);
  return res.json();
}

// 저장했으면 null, 못 했으면 화면에 보일 이유
export async function saveNickname(nickname: string): Promise<string | null> {
  if (!NICKNAME_PATTERN.test(nickname)) return `닉네임은 ${NICKNAME_RULE}로 써 주세요`;
  const res = await apiFetch("/api/me/nickname", {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ nickname }),
  });
  if (res.ok) return null;
  if (res.status === 401) return "로그인이 끊겼습니다. 다시 로그인해 주세요";
  const problem: { detail?: string } | null = await res.json().catch(() => null);
  // 서버가 우리 문장(detail)으로 이유를 준다. 없으면 일반 안내
  return problem?.detail && !problem.detail.startsWith("Invalid") ? problem.detail : "닉네임을 저장하지 못했습니다";
}

export async function logout(): Promise<void> {
  await apiFetch("/api/auth/logout", { method: "POST" });
}
