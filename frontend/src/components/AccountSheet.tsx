"use client";

import { useEffect, useRef, useState } from "react";
import { CloseIcon } from "@/components/EventCard";
import { LOGIN_URL, logout, NICKNAME_RULE, saveNickname, type Me } from "@/lib/auth";

// 머리말 카드의 사람 버튼으로 여는 시트: 로그인, 닉네임 정하기, 로그아웃.
// 로그인 직후 닉네임이 없으면 저절로 열린다
export default function AccountSheet({
  me,
  loginFailed,
  onChanged,
  onClose,
}: {
  me: Me;
  loginFailed: boolean;
  onChanged: () => void;
  onClose: () => void;
}) {
  const headingRef = useRef<HTMLHeadingElement>(null);
  const [editing, setEditing] = useState(false);

  useEffect(() => {
    headingRef.current?.focus();
  }, []);

  // Esc와 ←/→는 이 시트가 먼저 받는다 (지도까지 가면 스냅샷이 넘어간다)
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key !== "Escape" && event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
      event.stopPropagation();
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey, { capture: true });
    return () => window.removeEventListener("keydown", onKey, { capture: true });
  }, [onClose]);

  const title = !me.loggedIn ? "로그인" : me.needsNickname ? "닉네임 정하기" : "내 정보";

  return (
    <div className="absolute inset-0 z-30 flex flex-col justify-end bg-ink/30" onClick={onClose}>
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="account-sheet-title"
        onClick={(event) => event.stopPropagation()}
        className="flex flex-col gap-4 rounded-t-3xl bg-white px-5 pt-5 pb-[calc(24px+env(safe-area-inset-bottom))]"
      >
        <div className="flex items-center justify-between gap-3">
          <h2
            id="account-sheet-title"
            ref={headingRef}
            tabIndex={-1}
            className="text-xl font-extrabold tracking-tight outline-none"
          >
            {title}
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label={`${title} 닫기`}
            className="flex size-11 shrink-0 items-center justify-center rounded-full bg-fill"
          >
            <CloseIcon />
          </button>
        </div>

        {!me.loggedIn && <LoginButtons failed={loginFailed} />}
        {me.loggedIn && (me.needsNickname || editing) && (
          <NicknameForm
            current={me.nickname}
            onSaved={() => {
              setEditing(false);
              onChanged();
            }}
          />
        )}
        {me.loggedIn && !me.needsNickname && !editing && (
          <Profile
            nickname={me.nickname ?? ""}
            onEdit={() => setEditing(true)}
            onLogout={() => {
              logout()
                .catch((error: unknown) => console.error(error))
                .finally(onChanged);
            }}
          />
        )}
      </div>
    </div>
  );
}

function LoginButtons({ failed }: { failed: boolean }) {
  return (
    <div className="flex flex-col gap-3">
      {failed && (
        <p role="alert" className="rounded-2xl bg-fill px-4 py-3 text-sm font-medium">
          로그인하지 못했습니다. 다시 시도해 주세요.
        </p>
      )}
      <p className="text-sm leading-relaxed text-muted">
        게시판에 글과 댓글을 쓰려면 로그인합니다. 읽기는 로그인 없이 됩니다. 비밀번호와 이메일은 받지 않고, 카카오·구글의
        회원 번호만 씁니다.
      </p>
      {/* 화면 전체를 넘겨야 하므로 버튼이 아니라 링크다 */}
      <a
        href={LOGIN_URL.kakao}
        className="flex h-[52px] items-center justify-center rounded-full bg-[#fee500] text-[15px] font-bold text-[#191919]"
      >
        카카오로 계속하기
      </a>
      <a
        href={LOGIN_URL.google}
        className="flex h-[52px] items-center justify-center rounded-full border border-rule bg-white text-[15px] font-bold"
      >
        구글로 계속하기
      </a>
    </div>
  );
}

function NicknameForm({ current, onSaved }: { current: string | null; onSaved: () => void }) {
  const [value, setValue] = useState(current ?? "");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    setSaving(true);
    saveNickname(value.trim())
      .then((problem) => {
        if (problem) setError(problem);
        else onSaved();
      })
      .catch((e: unknown) => {
        console.error(e);
        setError("닉네임을 저장하지 못했습니다");
      })
      .finally(() => setSaving(false));
  };

  return (
    <form onSubmit={submit} className="flex flex-col gap-3">
      <label htmlFor="nickname" className="text-sm text-muted">
        게시판에 보일 이름입니다. {NICKNAME_RULE}, 정한 뒤 30일 동안 바꿀 수 없습니다.
      </label>
      <input
        id="nickname"
        value={value}
        onChange={(event) => {
          setValue(event.target.value);
          setError(null);
        }}
        maxLength={12}
        autoComplete="off"
        aria-invalid={error !== null}
        aria-describedby={error ? "nickname-error" : undefined}
        className="h-[52px] rounded-2xl bg-fill px-4 text-[16px] font-bold outline-none focus:ring-2 focus:ring-ink"
      />
      {error && (
        <p id="nickname-error" role="alert" className="text-sm font-medium text-[#b3261e]">
          {error}
        </p>
      )}
      <button
        type="submit"
        disabled={saving || value.trim().length === 0}
        className="h-[52px] rounded-full bg-ink text-[15px] font-bold text-white disabled:opacity-45"
      >
        {saving ? "저장하는 중…" : "저장"}
      </button>
    </form>
  );
}

function Profile({ nickname, onEdit, onLogout }: { nickname: string; onEdit: () => void; onLogout: () => void }) {
  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center justify-between gap-3 rounded-2xl bg-fill px-4 py-3">
        <div className="flex min-w-0 flex-col">
          <span className="text-xs text-muted">닉네임</span>
          <span className="truncate text-[17px] font-extrabold">{nickname}</span>
        </div>
        <button type="button" onClick={onEdit} className="h-11 shrink-0 rounded-full bg-white px-4 text-sm font-bold">
          바꾸기
        </button>
      </div>
      <button
        type="button"
        onClick={onLogout}
        className="h-[52px] rounded-full border border-rule bg-white text-[15px] font-bold"
      >
        로그아웃
      </button>
    </div>
  );
}
