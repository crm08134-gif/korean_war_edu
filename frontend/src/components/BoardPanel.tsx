"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { CloseIcon } from "@/components/EventCard";
import type { Me } from "@/lib/auth";
import {
  BOARD_LABEL,
  createComment,
  createPost,
  deleteComment,
  deletePost,
  editComment,
  editPost,
  fetchPost,
  fetchPosts,
  formatPostTime,
  LIMITS,
  setCommentLike,
  setPostLike,
  type BoardType,
  type CommentView,
  type PostDetail,
  type PostSummary,
} from "@/lib/board";
import { formatEventDate, type WarEvent } from "@/lib/events";

// 드롭다운 한 줄: 사건이나 인물
type Target = { id: string; label: string };

type View = { kind: "list" } | { kind: "post"; id: number } | { kind: "write"; editing: PostDetail | null };

const BLINDED_TEXT = "신고로 가려진 글입니다";

// 하단 패널의 "게시판" 탭으로 여는 화면 전체 게시판.
// 사건 게시판·인물 게시판을 고르고, 드롭다운에서 "전체" 또는 사건(날짜순)·인물(이름순) 하나를 고른다.
// 글 내용은 글자로만 보인다(HTML로 해석하지 않는다, CLAUDE.md 7장)
export default function BoardPanel({
  events,
  me,
  onNeedLogin,
  onClose,
}: {
  events: WarEvent[];
  me: Me;
  onNeedLogin: () => void;
  onClose: () => void;
}) {
  const [board, setBoard] = useState<BoardType>("EVENT");
  // 빈 문자열이 "전체"
  const [targetId, setTargetId] = useState("");
  const [view, setView] = useState<View>({ kind: "list" });
  // 글을 쓰거나 지운 뒤 목록을 새로 읽게 한다
  const [listVersion, setListVersion] = useState(0);
  const titleRef = useRef<HTMLHeadingElement>(null);

  const targets = useMemo(() => buildTargets(events), [events]);
  const options = targets[board];
  const labelOf = useMemo(() => {
    const map = new Map<string, string>();
    for (const t of [...targets.EVENT, ...targets.PERSON]) map.set(t.id, t.label);
    return (id: string) => map.get(id) ?? id;
  }, [targets]);

  const canWrite = me.loggedIn && !me.needsNickname;

  useEffect(() => {
    if (view.kind === "list") titleRef.current?.focus();
  }, [view.kind]);

  // Esc: 글·글쓰기에서는 목록으로, 목록이면 게시판을 닫는다
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key !== "Escape") return;
      if (view.kind === "list") onClose();
      else setView({ kind: "list" });
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [view.kind, onClose]);

  const startWriting = () => {
    if (!canWrite) {
      onNeedLogin();
      return;
    }
    setView({ kind: "write", editing: null });
  };

  const backToList = (reload: boolean) => {
    if (reload) setListVersion((v) => v + 1);
    setView({ kind: "list" });
  };

  return (
    <div role="dialog" aria-modal="true" aria-label="게시판" className="absolute inset-0 z-20 flex flex-col bg-white">
      <div className="flex items-center justify-between gap-3 px-5 pt-5 pb-3">
        {view.kind === "list" ? (
          <h2 ref={titleRef} tabIndex={-1} className="text-[22px] font-extrabold tracking-tight outline-none">
            게시판
          </h2>
        ) : (
          <button
            type="button"
            onClick={() => backToList(false)}
            className="-ml-2 flex h-11 items-center gap-1 rounded-full px-2 text-sm font-medium"
          >
            <BackIcon />
            목록
          </button>
        )}
        <button
          type="button"
          onClick={onClose}
          aria-label="게시판 닫기"
          className="-mr-2.5 flex size-11 shrink-0 items-center justify-center rounded-full text-muted"
        >
          <CloseIcon />
        </button>
      </div>

      {view.kind === "list" && (
        <div className="flex flex-col gap-3 px-5 pb-3">
          {/* 사건 게시판 / 인물 게시판 */}
          <div role="tablist" aria-label="게시판 고르기" className="flex rounded-full bg-fill p-1">
            {(["EVENT", "PERSON"] as const).map((b) => (
              <button
                key={b}
                type="button"
                role="tab"
                aria-selected={board === b}
                onClick={() => {
                  setBoard(b);
                  setTargetId("");
                }}
                className={`h-10 flex-1 rounded-full text-sm font-bold ${board === b ? "bg-white shadow-sm" : "text-muted"}`}
              >
                {BOARD_LABEL[b]}
              </button>
            ))}
          </div>
          <div className="flex gap-2">
            <TargetSelect
              label={board === "EVENT" ? "사건 고르기" : "인물 고르기"}
              value={targetId}
              options={options}
              allLabel={board === "EVENT" ? "전체 사건" : "전체 인물"}
              onChange={setTargetId}
            />
            <button
              type="button"
              onClick={startWriting}
              className="h-11 shrink-0 rounded-full bg-ink px-4 text-sm font-bold text-white"
            >
              글쓰기
            </button>
          </div>
        </div>
      )}

      <div className="min-h-0 flex-1 overflow-y-auto px-5 pt-2 pb-[calc(24px+env(safe-area-inset-bottom))]">
        {view.kind === "list" && (
          <PostList
            key={`${board}-${targetId}-${listVersion}`}
            board={board}
            targetId={targetId || null}
            showTarget={!targetId}
            labelOf={labelOf}
            onOpen={(id) => setView({ kind: "post", id })}
          />
        )}
        {view.kind === "post" && (
          <PostView
            key={view.id}
            id={view.id}
            canWrite={canWrite}
            labelOf={labelOf}
            onNeedLogin={onNeedLogin}
            onEdit={(post) => setView({ kind: "write", editing: post })}
            onDeleted={() => backToList(true)}
          />
        )}
        {view.kind === "write" && (
          <WriteForm
            board={view.editing?.board ?? board}
            options={targets[view.editing?.board ?? board]}
            initialTarget={view.editing?.targetId ?? targetId}
            editing={view.editing}
            labelOf={labelOf}
            onSaved={(id) => {
              setListVersion((v) => v + 1);
              setView({ kind: "post", id });
            }}
          />
        )}
      </div>
    </div>
  );
}

// 사건은 날짜순(받은 순서가 날짜순이지만 한 번 더 정렬), 인물은 이름순. 인물은 여러 사건에 나오므로 한 번만
function buildTargets(events: WarEvent[]): Record<BoardType, Target[]> {
  const eventTargets = [...events]
    .sort((a, b) => a.date.localeCompare(b.date))
    .map((event) => ({ id: event.id, label: `${event.title} (${formatEventDate(event)})` }));
  const people = new Map<string, string>();
  for (const event of events) {
    for (const person of event.people) people.set(person.id, person.name);
  }
  const personTargets = [...people.entries()]
    .map(([id, name]) => ({ id, label: name }))
    .sort((a, b) => a.label.localeCompare(b.label, "ko"));
  return { EVENT: eventTargets, PERSON: personTargets };
}

function TargetSelect({
  label,
  value,
  options,
  allLabel,
  onChange,
}: {
  label: string;
  value: string;
  options: Target[];
  allLabel: string | null;
  onChange: (id: string) => void;
}) {
  return (
    <select
      aria-label={label}
      value={value}
      onChange={(event) => onChange(event.target.value)}
      className="h-11 min-w-0 flex-1 rounded-full bg-fill px-4 text-sm font-medium"
    >
      {allLabel !== null ? <option value="">{allLabel}</option> : <option value="">골라 주세요</option>}
      {options.map((t) => (
        <option key={t.id} value={t.id}>
          {t.label}
        </option>
      ))}
    </select>
  );
}

// ---------- 목록 ----------

type ListLoad =
  | { status: "loading" }
  | { status: "error" }
  | { status: "ready"; posts: PostSummary[]; nextCursor: number | null };

function PostList({
  board,
  targetId,
  showTarget,
  labelOf,
  onOpen,
}: {
  board: BoardType;
  targetId: string | null;
  showTarget: boolean;
  labelOf: (id: string) => string;
  onOpen: (id: number) => void;
}) {
  const [load, setLoad] = useState<ListLoad>({ status: "loading" });
  const [loadingMore, setLoadingMore] = useState(false);

  // 게시판·대상이 바뀌면 key가 바뀌어 새로 그려지므로 처음 한 번만 읽는다
  useEffect(() => {
    const controller = new AbortController();
    fetchPosts(board, targetId, null, controller.signal)
      .then((page) => setLoad({ status: "ready", ...page }))
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          console.error(error);
          setLoad({ status: "error" });
        }
      });
    return () => controller.abort();
  }, [board, targetId]);

  if (load.status === "loading") return <Notice>글을 불러오는 중…</Notice>;
  if (load.status === "error") return <Notice>글을 불러오지 못했습니다. 백엔드가 켜져 있는지 확인해 주세요.</Notice>;
  if (load.posts.length === 0) return <Notice>아직 글이 없습니다. 첫 글을 써 보세요.</Notice>;

  const more = () => {
    if (load.nextCursor === null) return;
    setLoadingMore(true);
    fetchPosts(board, targetId, load.nextCursor)
      .then((page) => setLoad({ status: "ready", posts: [...load.posts, ...page.posts], nextCursor: page.nextCursor }))
      .catch((error: unknown) => console.error(error))
      .finally(() => setLoadingMore(false));
  };

  return (
    <div className="flex flex-col gap-3">
      <ul className="flex flex-col">
        {load.posts.map((post) => (
          <li key={post.id} className="border-b border-rule-soft last:border-b-0">
            <button type="button" onClick={() => onOpen(post.id)} className="flex w-full flex-col gap-1 py-3.5 text-left">
              {showTarget && <span className="truncate text-xs font-medium text-muted">{labelOf(post.targetId)}</span>}
              <span className={`line-clamp-2 text-[15px] leading-snug font-bold ${post.title === null ? "text-muted" : ""}`}>
                {post.title ?? BLINDED_TEXT}
                {post.blinded && post.mine && <span className="ml-1.5 text-xs font-medium text-muted">(가려짐)</span>}
              </span>
              <span className="flex items-center gap-2 text-xs text-muted tabular-nums">
                <span className="truncate">{post.author}</span>
                <span>·</span>
                <span className="shrink-0">{formatPostTime(post.createdAt)}</span>
                <span className="ml-auto flex shrink-0 items-center gap-1">
                  <HeartIcon filled={post.liked} />
                  {post.likes}
                </span>
              </span>
            </button>
          </li>
        ))}
      </ul>
      {load.nextCursor !== null && (
        <button
          type="button"
          onClick={more}
          disabled={loadingMore}
          className="h-11 rounded-full bg-fill text-sm font-bold disabled:opacity-45"
        >
          {loadingMore ? "불러오는 중…" : "더 보기"}
        </button>
      )}
    </div>
  );
}

// ---------- 글 본문 ----------

type PostLoad = { status: "loading" } | { status: "error" } | { status: "missing" } | { status: "ready"; post: PostDetail };

function PostView({
  id,
  canWrite,
  labelOf,
  onNeedLogin,
  onEdit,
  onDeleted,
}: {
  id: number;
  canWrite: boolean;
  labelOf: (id: string) => string;
  onNeedLogin: () => void;
  onEdit: (post: PostDetail) => void;
  onDeleted: () => void;
}) {
  const [load, setLoad] = useState<PostLoad>({ status: "loading" });
  // 공감·댓글 뒤에 다시 읽는다
  const [version, setVersion] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    fetchPost(id, controller.signal)
      .then((post) => setLoad(post ? { status: "ready", post } : { status: "missing" }))
      .catch((e: unknown) => {
        if (!controller.signal.aborted) {
          console.error(e);
          setLoad({ status: "error" });
        }
      });
    return () => controller.abort();
  }, [id, version]);

  if (load.status === "loading") return <Notice>글을 불러오는 중…</Notice>;
  if (load.status === "error") return <Notice>글을 불러오지 못했습니다.</Notice>;
  if (load.status === "missing") return <Notice>지워졌거나 없는 글입니다.</Notice>;
  const post = load.post;
  const hidden = post.title === null;

  // 쓰기 요청을 보내고, 성공하면 다시 읽는다. 실패하면 서버가 준 이유를 보인다
  const act = async (task: () => Promise<{ ok: true } | { ok: false; reason: string }>, after?: () => void) => {
    setBusy(true);
    setError(null);
    const result = await task();
    setBusy(false);
    if (!result.ok) {
      setError(result.reason);
      return;
    }
    if (after) after();
    else setVersion((v) => v + 1);
  };

  const toggleLike = () => {
    if (!canWrite) {
      onNeedLogin();
      return;
    }
    void act(() => setPostLike(post.id, !post.liked));
  };

  return (
    <article className="flex flex-col gap-4">
      <header className="flex flex-col gap-1.5">
        <p className="truncate text-xs font-medium text-muted">
          {BOARD_LABEL[post.board]} · {labelOf(post.targetId)}
        </p>
        <h3 className={`text-xl leading-snug font-extrabold tracking-tight ${hidden ? "text-muted" : ""}`}>
          {post.title ?? BLINDED_TEXT}
        </h3>
        <p className="text-xs text-muted tabular-nums">
          {post.author} · {formatPostTime(post.createdAt)}
          {post.updatedAt && " · 수정됨"}
        </p>
      </header>

      {post.blinded && post.mine && (
        <p role="status" className="rounded-2xl bg-fill px-4 py-3 text-sm">
          신고가 쌓여 가려진 글입니다. 다른 사람에게는 내용이 보이지 않고, 운영자가 검토합니다.
        </p>
      )}

      {post.body !== null && (
        // 글자로만 보인다. 줄바꿈은 그대로 살린다
        <p className="text-[15px] leading-relaxed break-words whitespace-pre-wrap">{post.body}</p>
      )}

      <div className="flex flex-wrap items-center gap-2">
        <button
          type="button"
          onClick={toggleLike}
          disabled={busy || hidden}
          aria-pressed={post.liked}
          className="inline-flex h-10 items-center gap-1.5 rounded-full bg-fill px-4 text-sm font-bold disabled:opacity-45"
        >
          <HeartIcon filled={post.liked} />
          공감 {post.likes}
        </button>
        {post.mine && !confirmDelete && (
          <>
            {!post.blinded && (
              <button
                type="button"
                onClick={() => onEdit(post)}
                className="h-10 rounded-full px-3 text-sm font-medium text-muted"
              >
                고치기
              </button>
            )}
            <button
              type="button"
              onClick={() => setConfirmDelete(true)}
              className="h-10 rounded-full px-3 text-sm font-medium text-muted"
            >
              지우기
            </button>
          </>
        )}
        {post.mine && confirmDelete && (
          <span className="flex items-center gap-1">
            <span className="text-sm">지울까요?</span>
            <button
              type="button"
              disabled={busy}
              onClick={() => void act(() => deletePost(post.id), onDeleted)}
              className="h-10 rounded-full px-3 text-sm font-bold text-[#b3261e]"
            >
              지우기
            </button>
            <button
              type="button"
              onClick={() => setConfirmDelete(false)}
              className="h-10 rounded-full px-3 text-sm font-medium text-muted"
            >
              그대로 두기
            </button>
          </span>
        )}
      </div>

      {error && (
        <p role="alert" className="text-sm font-medium text-[#b3261e]">
          {error}
        </p>
      )}

      <Comments
        post={post}
        canWrite={canWrite}
        onNeedLogin={onNeedLogin}
        onChanged={() => setVersion((v) => v + 1)}
      />
    </article>
  );
}

// ---------- 댓글 ----------

function Comments({
  post,
  canWrite,
  onNeedLogin,
  onChanged,
}: {
  post: PostDetail;
  canWrite: boolean;
  onNeedLogin: () => void;
  onChanged: () => void;
}) {
  const [draft, setDraft] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  // 가려진 글에는 댓글을 달 수 없다 (서버도 막는다)
  const closed = post.blinded;

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!draft.trim()) return;
    setBusy(true);
    setError(null);
    const result = await createComment(post.id, draft);
    setBusy(false);
    if (!result.ok) {
      setError(result.reason);
      return;
    }
    setDraft("");
    onChanged();
  };

  return (
    <section className="flex flex-col gap-3 border-t border-rule-soft pt-4">
      <h4 className="text-[15px] font-bold">
        댓글 <span className="text-muted tabular-nums">{post.comments.length}</span>
      </h4>

      {post.comments.length > 0 && (
        <ul className="flex flex-col gap-3">
          {post.comments.map((comment) => (
            <li key={comment.id}>
              <CommentItem comment={comment} canWrite={canWrite} onNeedLogin={onNeedLogin} onChanged={onChanged} />
            </li>
          ))}
        </ul>
      )}

      {!closed &&
        (canWrite ? (
          <form onSubmit={submit} className="flex flex-col gap-2">
            <textarea
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              maxLength={LIMITS.comment}
              rows={3}
              aria-label="댓글"
              placeholder="댓글을 써 주세요"
              className="w-full resize-none rounded-2xl bg-fill px-4 py-3 text-[15px]"
            />
            <div className="flex items-center justify-between">
              <span className="text-xs text-muted tabular-nums">
                {draft.length}/{LIMITS.comment}
              </span>
              <button
                type="submit"
                disabled={busy || !draft.trim()}
                className="h-10 rounded-full bg-ink px-4 text-sm font-bold text-white disabled:opacity-45"
              >
                댓글 쓰기
              </button>
            </div>
          </form>
        ) : (
          <button type="button" onClick={onNeedLogin} className="h-11 rounded-full bg-fill text-sm font-bold">
            로그인하고 댓글 쓰기
          </button>
        ))}

      {error && (
        <p role="alert" className="text-sm font-medium text-[#b3261e]">
          {error}
        </p>
      )}
    </section>
  );
}

function CommentItem({
  comment,
  canWrite,
  onNeedLogin,
  onChanged,
}: {
  comment: CommentView;
  canWrite: boolean;
  onNeedLogin: () => void;
  onChanged: () => void;
}) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(comment.body ?? "");
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const act = async (task: () => ReturnType<typeof deleteComment>) => {
    setBusy(true);
    setError(null);
    const result = await task();
    setBusy(false);
    if (!result.ok) {
      setError(result.reason);
      return;
    }
    setEditing(false);
    setConfirmDelete(false);
    onChanged();
  };

  const toggleLike = () => {
    if (!canWrite) {
      onNeedLogin();
      return;
    }
    void act(() => setCommentLike(comment.id, !comment.liked));
  };

  return (
    <div className="flex flex-col gap-1.5 rounded-2xl bg-fill px-4 py-3">
      <p className="text-xs text-muted tabular-nums">
        <span className="font-bold text-ink">{comment.author}</span> · {formatPostTime(comment.createdAt)}
        {comment.updatedAt && " · 수정됨"}
      </p>

      {editing ? (
        <form
          onSubmit={(event) => {
            event.preventDefault();
            if (draft.trim()) void act(() => editComment(comment.id, draft));
          }}
          className="flex flex-col gap-2"
        >
          <textarea
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            maxLength={LIMITS.comment}
            rows={3}
            aria-label="댓글 고치기"
            className="w-full resize-none rounded-xl bg-white px-3 py-2 text-[15px]"
          />
          <div className="flex justify-end gap-1">
            <button
              type="button"
              onClick={() => setEditing(false)}
              className="h-9 rounded-full px-3 text-sm font-medium text-muted"
            >
              그만두기
            </button>
            <button
              type="submit"
              disabled={busy || !draft.trim()}
              className="h-9 rounded-full bg-ink px-3 text-sm font-bold text-white disabled:opacity-45"
            >
              저장
            </button>
          </div>
        </form>
      ) : (
        <p
          className={`text-[15px] leading-relaxed break-words whitespace-pre-wrap ${comment.body === null ? "text-muted" : ""}`}
        >
          {comment.body ?? "신고로 가려진 댓글입니다"}
        </p>
      )}

      {!editing && (
        <div className="-ml-2 flex flex-wrap items-center">
          <button
            type="button"
            onClick={toggleLike}
            disabled={busy || comment.body === null}
            aria-pressed={comment.liked}
            aria-label={`공감 ${comment.likes}`}
            className="inline-flex h-9 items-center gap-1 rounded-full px-2 text-xs font-bold text-muted disabled:opacity-45"
          >
            <HeartIcon filled={comment.liked} />
            {comment.likes}
          </button>
          {comment.mine && !confirmDelete && (
            <>
              {!comment.blinded && (
                <button
                  type="button"
                  onClick={() => {
                    setDraft(comment.body ?? "");
                    setEditing(true);
                  }}
                  className="h-9 rounded-full px-2 text-xs font-medium text-muted"
                >
                  고치기
                </button>
              )}
              <button
                type="button"
                onClick={() => setConfirmDelete(true)}
                className="h-9 rounded-full px-2 text-xs font-medium text-muted"
              >
                지우기
              </button>
            </>
          )}
          {comment.mine && confirmDelete && (
            <>
              <span className="px-2 text-xs">지울까요?</span>
              <button
                type="button"
                disabled={busy}
                onClick={() => void act(() => deleteComment(comment.id))}
                className="h-9 rounded-full px-2 text-xs font-bold text-[#b3261e]"
              >
                지우기
              </button>
              <button
                type="button"
                onClick={() => setConfirmDelete(false)}
                className="h-9 rounded-full px-2 text-xs font-medium text-muted"
              >
                그대로 두기
              </button>
            </>
          )}
        </div>
      )}

      {error && (
        <p role="alert" className="text-xs font-medium text-[#b3261e]">
          {error}
        </p>
      )}
    </div>
  );
}

// ---------- 글쓰기·고치기 ----------

function WriteForm({
  board,
  options,
  initialTarget,
  editing,
  labelOf,
  onSaved,
}: {
  board: BoardType;
  options: Target[];
  initialTarget: string;
  editing: PostDetail | null;
  labelOf: (id: string) => string;
  onSaved: (id: number) => void;
}) {
  const [target, setTarget] = useState(initialTarget);
  const [title, setTitle] = useState(editing?.title ?? "");
  const [body, setBody] = useState(editing?.body ?? "");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const ready = (editing || target) && title.trim() && body.trim();

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!ready) return;
    setBusy(true);
    setError(null);
    const result = editing ? await editPost(editing.id, title, body) : await createPost(board, target, title, body);
    setBusy(false);
    if (!result.ok) {
      setError(result.reason);
      return;
    }
    onSaved(editing ? editing.id : (result.id ?? 0));
  };

  return (
    <form onSubmit={submit} className="flex flex-col gap-3">
      <h3 className="text-xl font-extrabold tracking-tight">{editing ? "글 고치기" : `${BOARD_LABEL[board]}에 글쓰기`}</h3>

      {editing ? (
        <p className="truncate text-sm text-muted">{labelOf(editing.targetId)}</p>
      ) : (
        <div className="flex">
          <TargetSelect
            label={board === "EVENT" ? "어느 사건에 쓸까요" : "어느 인물에 쓸까요"}
            value={target}
            options={options}
            allLabel={null}
            onChange={setTarget}
          />
        </div>
      )}

      <input
        value={title}
        onChange={(event) => setTitle(event.target.value)}
        maxLength={LIMITS.title}
        aria-label="제목"
        placeholder="제목"
        className="h-12 w-full rounded-2xl bg-fill px-4 text-[15px] font-bold"
      />
      <textarea
        value={body}
        onChange={(event) => setBody(event.target.value)}
        maxLength={LIMITS.body}
        rows={10}
        aria-label="본문"
        placeholder="사료를 근거로 한 서로 다른 해석과 토론을 환영합니다. 특정 집단에 대한 혐오, 근거 없는 비방, 피해의 조롱, 개인정보 노출은 가려질 수 있습니다."
        className="w-full resize-y rounded-2xl bg-fill px-4 py-3 text-[15px] leading-relaxed"
      />
      <div className="flex items-center justify-between">
        <span className="text-xs text-muted tabular-nums">
          {body.length}/{LIMITS.body}
        </span>
        <button
          type="submit"
          disabled={busy || !ready}
          className="h-11 rounded-full bg-ink px-5 text-sm font-bold text-white disabled:opacity-45"
        >
          {busy ? "저장 중…" : editing ? "고치기" : "올리기"}
        </button>
      </div>

      {error && (
        <p role="alert" className="text-sm font-medium text-[#b3261e]">
          {error}
        </p>
      )}
    </form>
  );
}

// ---------- 작은 것들 ----------

function Notice({ children }: { children: React.ReactNode }) {
  return <p className="py-10 text-center text-sm text-muted">{children}</p>;
}

function BackIcon() {
  return (
    <svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true">
      <path
        d="M7.5 2.5 4 6l3.5 3.5"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

// 공감 하트. 누르면 속을 채운다 (직접 그린 단순한 도형)
function HeartIcon({ filled }: { filled: boolean }) {
  return (
    <svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true">
      <path
        d="M7 12.2 2.2 7.6A2.9 2.9 0 0 1 7 3.6a2.9 2.9 0 0 1 4.8 4Z"
        fill={filled ? "currentColor" : "none"}
        stroke="currentColor"
        strokeWidth="1.4"
        strokeLinejoin="round"
      />
    </svg>
  );
}
