// 게시판 API. 백엔드 BoardController와 같은 모양
import { apiFetch } from "@/lib/api";

export type BoardType = "EVENT" | "PERSON";

export const BOARD_LABEL: Record<BoardType, string> = {
  EVENT: "사건 게시판",
  PERSON: "인물 게시판",
};

// 백엔드 요청 DTO와 같은 한도. 서버와 DB가 다시 검사한다
export const LIMITS = { title: 100, body: 5000, comment: 1000 } as const;

// 가려진 글을 남이 보면 title이 null이다
export type PostSummary = {
  id: number;
  targetId: string;
  title: string | null;
  author: string;
  blinded: boolean;
  likes: number;
  liked: boolean;
  mine: boolean;
  createdAt: string;
};

export type PostPage = { posts: PostSummary[]; nextCursor: number | null };

export type CommentView = {
  id: number;
  body: string | null;
  author: string;
  blinded: boolean;
  likes: number;
  liked: boolean;
  mine: boolean;
  createdAt: string;
  updatedAt: string | null;
};

export type PostDetail = {
  id: number;
  board: BoardType;
  targetId: string;
  title: string | null;
  body: string | null;
  author: string;
  blinded: boolean;
  likes: number;
  liked: boolean;
  mine: boolean;
  createdAt: string;
  updatedAt: string | null;
  comments: CommentView[];
};

// 읽기는 공개다. 다만 만료된 로그인 쿠키가 남아 있으면 401이 오는데, apiFetch가 갱신을 시도하고
// 그것도 실패하면 서버가 쿠키를 지운다. 그때는 비로그인으로 한 번 더 읽는다
async function read(path: string, signal?: AbortSignal): Promise<Response> {
  const res = await apiFetch(path, { signal });
  return res.status === 401 ? fetch(path, { signal }) : res;
}

export async function fetchPosts(
  board: BoardType,
  targetId: string | null,
  cursor: number | null,
  signal?: AbortSignal,
): Promise<PostPage> {
  const params = new URLSearchParams({ board });
  if (targetId) params.set("targetId", targetId);
  if (cursor !== null) params.set("cursor", String(cursor));
  const res = await read(`/api/posts?${params}`, signal);
  if (!res.ok) throw new Error(`GET /api/posts ${res.status}`);
  return res.json();
}

// 없거나 지운 글이면 null
export async function fetchPost(id: number, signal?: AbortSignal): Promise<PostDetail | null> {
  const res = await read(`/api/posts/${id}`, signal);
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`GET /api/posts/${id} ${res.status}`);
  return res.json();
}

// 쓰기 결과: 성공이면 ok와 (만들었으면) 새 번호, 실패면 화면에 보일 이유
export type Result = { ok: true; id: number | null } | { ok: false; reason: string };

async function send(path: string, method: string, body?: unknown): Promise<Result> {
  const res = await apiFetch(path, {
    method,
    headers: body === undefined ? undefined : { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (res.ok) {
    const created: { id?: number } | null = res.status === 201 ? await res.json().catch(() => null) : null;
    return { ok: true, id: created?.id ?? null };
  }
  return { ok: false, reason: await reasonOf(res) };
}

async function reasonOf(res: Response): Promise<string> {
  if (res.status === 401) return "로그인이 필요합니다. 다시 로그인해 주세요";
  const problem: { detail?: string } | null = await res.json().catch(() => null);
  // 서버가 우리 문장(detail)으로 이유를 준다. 영어 기본 문장이면 일반 안내
  const detail = problem?.detail;
  if (detail && !detail.startsWith("Invalid") && !/^[A-Za-z]/.test(detail)) return detail;
  return "요청을 처리하지 못했습니다. 잠시 뒤 다시 해 주세요";
}

export function createPost(board: BoardType, targetId: string, title: string, body: string): Promise<Result> {
  return send("/api/posts", "POST", { board, targetId, title, body });
}

export function editPost(id: number, title: string, body: string): Promise<Result> {
  return send(`/api/posts/${id}`, "PATCH", { title, body });
}

export function deletePost(id: number): Promise<Result> {
  return send(`/api/posts/${id}`, "DELETE");
}

export function createComment(postId: number, body: string): Promise<Result> {
  return send(`/api/posts/${postId}/comments`, "POST", { body });
}

export function editComment(id: number, body: string): Promise<Result> {
  return send(`/api/comments/${id}`, "PATCH", { body });
}

export function deleteComment(id: number): Promise<Result> {
  return send(`/api/comments/${id}`, "DELETE");
}

// 누름은 PUT, 취소는 DELETE (두 번 보내도 결과가 같다)
export function setPostLike(id: number, like: boolean): Promise<Result> {
  return send(`/api/posts/${id}/like`, like ? "PUT" : "DELETE");
}

export function setCommentLike(id: number, like: boolean): Promise<Result> {
  return send(`/api/comments/${id}/like`, like ? "PUT" : "DELETE");
}

// "2026. 11. 2. 14:03"
const DATE_TIME = new Intl.DateTimeFormat("ko-KR", {
  year: "numeric",
  month: "numeric",
  day: "numeric",
  hour: "2-digit",
  minute: "2-digit",
  hour12: false,
});

export function formatPostTime(iso: string): string {
  return DATE_TIME.format(new Date(iso));
}

// ---------- 신고 (4단계) ----------

// 운영 정책의 금지 항목과 같다 (백엔드 ReportReason)
export type ReportReason = "HATE" | "DEFAMATION" | "MOCKERY" | "PRIVACY" | "SPAM" | "OTHER";

export const REPORT_REASONS: { value: ReportReason; label: string }[] = [
  { value: "HATE", label: "특정 집단에 대한 혐오·비하" },
  { value: "DEFAMATION", label: "실존 인물·유족에 대한 근거 없는 비방" },
  { value: "MOCKERY", label: "학살·전쟁범죄 피해의 조롱·정당화" },
  { value: "PRIVACY", label: "개인정보 노출" },
  { value: "SPAM", label: "광고·도배" },
  { value: "OTHER", label: "기타 (이유를 적어 주세요)" },
];

export const REPORT_DETAIL_LIMIT = 500;

export type ReportKind = "posts" | "comments";

export function report(kind: ReportKind, id: number, reason: ReportReason, detail: string): Promise<Result> {
  return send(`/api/${kind}/${id}/report`, "POST", { reason, detail: detail.trim() || null });
}

// ---------- 관리자 큐 ----------

export type QueueItem = {
  targetType: "POST" | "COMMENT";
  targetId: number;
  postId: number;
  status: "VISIBLE" | "BLINDED" | "DELETED";
  title: string | null; // 댓글이면 null
  body: string;
  authorId: number;
  author: string | null; // 탈퇴했으면 null
  authorBannedUntil: string | null;
  reports: number;
  reasons: string; // "HATE,SPAM"
  details: string | null;
  lastReportedAt: string;
};

export async function fetchQueue(signal?: AbortSignal): Promise<QueueItem[]> {
  const res = await apiFetch("/api/admin/reports", { signal });
  if (!res.ok) throw new Error(`GET /api/admin/reports ${res.status}`);
  return res.json();
}

export function moderate(item: QueueItem, status: "VISIBLE" | "BLINDED"): Promise<Result> {
  const kind = item.targetType === "POST" ? "posts" : "comments";
  return send(`/api/admin/${kind}/${item.targetId}/status`, "PUT", { status });
}

// days가 0이면 제재를 푼다
export function ban(memberId: number, days: number): Promise<Result> {
  return send(`/api/admin/members/${memberId}/ban`, "PUT", { days });
}
