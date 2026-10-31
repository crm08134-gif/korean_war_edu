// 백엔드 API 부르기. 쓰기 요청에는 CSRF 머리글을 붙이고, 로그인 토큰이 만료돼 401이 오면 한 번 갱신하고 다시 보낸다.
// 로그인 토큰은 HttpOnly 쿠키라 자바스크립트가 읽지 못한다(XSS로 훔쳐 갈 수 없다). 브라우저가 알아서 실어 보낸다

const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);

// 백엔드가 심어 둔 XSRF-TOKEN 쿠키. 다른 사이트의 스크립트는 이 값을 읽을 수 없어서 머리글을 흉내 내지 못한다
function csrfToken(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : null;
}

function isWrite(init: RequestInit): boolean {
  return !SAFE_METHODS.has((init.method ?? "GET").toUpperCase());
}

function withCsrf(init: RequestInit): RequestInit {
  if (!isWrite(init)) return init;
  const headers = new Headers(init.headers);
  const token = csrfToken();
  if (token) headers.set("X-XSRF-TOKEN", token);
  return { ...init, headers };
}

// 아직 CSRF 쿠키가 없으면(첫 요청이 쓰기일 때) 읽기 요청을 한 번 보내 받아 둔다
async function ensureCsrfCookie(): Promise<void> {
  if (!csrfToken()) await fetch("/api/me");
}

let refreshing: Promise<boolean> | null = null;

// 토큰 갱신은 한 번에 하나만 한다. 같은 탭은 진행 중인 갱신을 같이 기다리고, 다른 탭과는 브라우저 잠금으로 줄을 선다.
// 리프레시 토큰은 한 번 쓰면 폐기되므로, 두 요청이 같은 토큰으로 동시에 갱신하면 서버가 탈취로 보고 로그인을 끊는다
export function refreshSession(): Promise<boolean> {
  refreshing ??= runExclusive(async () => {
    await ensureCsrfCookie();
    const res = await fetch("/api/auth/refresh", withCsrf({ method: "POST" }));
    return res.ok;
  }).finally(() => {
    refreshing = null;
  });
  return refreshing;
}

async function runExclusive<T>(task: () => Promise<T>): Promise<T> {
  if (typeof navigator !== "undefined" && navigator.locks) {
    return await navigator.locks.request("korean-war-auth-refresh", task);
  }
  return task();
}

export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  if (isWrite(init)) await ensureCsrfCookie();
  const res = await fetch(path, withCsrf(init));
  if (res.status !== 401 || path.startsWith("/api/auth/")) return res;
  // 액세스 토큰이 만료됐다. 갱신에 성공하면 같은 요청을 한 번만 다시 보낸다
  if (!(await refreshSession())) return res;
  return fetch(path, withCsrf(init));
}
