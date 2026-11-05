"use client";

import { useEffect, useRef, useState } from "react";
import { CloseIcon } from "@/components/EventCard";
import { askChat, MAX_QUESTION, type ChatTarget, type ChatTurn } from "@/lib/chat";

// 처음 열었을 때 눌러 볼 수 있는 질문
const SUGGESTIONS = ["배경을 더 쉽게 설명해 주세요", "이 일이 왜 중요했나요?", "그 뒤에 어떻게 됐나요?"];

// 사건·이야기 카드의 "AI에게 묻기" 버튼으로 여는 시트. 카드 내용을 바탕으로 AI(Gemini)가 답한다.
// 대화는 이 시트 안에만 있고, 닫으면 사라진다
export default function ChatSheet({
  target,
  title,
  canAsk,
  onNeedLogin,
  onClose,
}: {
  target: ChatTarget;
  title: string;
  // 로그인하고 닉네임을 정한 회원만 물을 수 있다
  canAsk: boolean;
  onNeedLogin: () => void;
  onClose: () => void;
}) {
  const headingRef = useRef<HTMLHeadingElement>(null);
  const endRef = useRef<HTMLDivElement>(null);
  const [turns, setTurns] = useState<ChatTurn[]>([]);
  const [question, setQuestion] = useState("");
  const [waiting, setWaiting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [remaining, setRemaining] = useState<number | null>(null);

  useEffect(() => {
    headingRef.current?.focus();
  }, []);

  // 새 말이 붙으면 맨 아래로 내린다
  useEffect(() => {
    endRef.current?.scrollIntoView({ block: "end" });
  }, [turns, waiting]);

  // Esc와 ←/→는 이 시트가 먼저 받는다. 지도 화면까지 가면 카드가 닫히거나 스냅샷이 넘어간다 (입력창의 커서 이동은 그대로 된다)
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key !== "Escape" && event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
      event.stopPropagation();
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey, { capture: true });
    return () => window.removeEventListener("keydown", onKey, { capture: true });
  }, [onClose]);

  const send = (text: string) => {
    const clean = text.trim();
    if (!clean || waiting) return;
    if (!canAsk) {
      onNeedLogin();
      return;
    }
    const history = turns;
    setTurns([...history, { role: "user", text: clean }]);
    setQuestion("");
    setError(null);
    setWaiting(true);
    askChat(target, history, clean)
      .then((result) => {
        setTurns((now) => [...now, { role: "model", text: result.answer }]);
        setRemaining(result.remaining);
      })
      .catch((e: unknown) => {
        // 답을 못 받은 질문은 지우고 입력창에 되돌려 다시 보낼 수 있게 한다
        setTurns(history);
        setQuestion(clean);
        setError(e instanceof Error ? e.message : "답변을 받지 못했습니다");
      })
      .finally(() => setWaiting(false));
  };

  return (
    <div className="absolute inset-0 z-20 flex flex-col justify-end bg-ink/30" onClick={onClose}>
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="chat-sheet-title"
        onClick={(event) => event.stopPropagation()}
        className="flex h-[80%] flex-col rounded-t-3xl bg-white"
      >
        <div className="flex items-center justify-between gap-3 px-5 pt-5 pb-2">
          <div className="flex min-w-0 flex-col">
            <p className="truncate text-[13px] font-medium text-muted">{title}</p>
            <h2
              id="chat-sheet-title"
              ref={headingRef}
              tabIndex={-1}
              className="text-xl font-extrabold tracking-tight outline-none"
            >
              AI에게 묻기
            </h2>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="AI에게 묻기 닫기"
            className="flex size-11 shrink-0 items-center justify-center rounded-full bg-fill"
          >
            <CloseIcon />
          </button>
        </div>

        <p className="px-5 pb-3 text-xs leading-relaxed text-muted">
          이 카드의 내용을 바탕으로 AI(구글 Gemini)가 답합니다. AI 답변은 틀릴 수 있으니 카드의 출처로 확인해 주세요.
          질문은 구글로 보내지므로 개인정보는 적지 마세요.
        </p>

        <div className="flex min-h-0 flex-1 flex-col gap-3 overflow-y-auto px-5 pb-3" aria-live="polite">
          {turns.length === 0 && (
            <div className="flex flex-wrap gap-2">
              {SUGGESTIONS.map((suggestion) => (
                <button
                  key={suggestion}
                  type="button"
                  onClick={() => send(suggestion)}
                  className="rounded-full bg-fill px-3.5 py-2 text-[13px] font-medium"
                >
                  {suggestion}
                </button>
              ))}
            </div>
          )}
          {turns.map((turn, i) =>
            turn.role === "user" ? (
              <p key={i} className="max-w-[85%] self-end rounded-2xl bg-ink px-4 py-2.5 text-[15px] leading-relaxed text-white">
                {turn.text}
              </p>
            ) : (
              // 답은 글자로만 보인다 (HTML로 해석하지 않는다). 줄바꿈만 살린다
              <div key={i} className="flex max-w-[92%] flex-col gap-1 self-start">
                <p className="rounded-2xl bg-fill px-4 py-3 text-[15px] leading-[1.75] whitespace-pre-line">{turn.text}</p>
                <span className="px-1 text-[11px] text-muted">AI 답변 · 틀릴 수 있음</span>
              </div>
            ),
          )}
          {waiting && <p className="self-start rounded-2xl bg-fill px-4 py-3 text-[15px] text-muted">답을 쓰는 중…</p>}
          {error && (
            <p role="alert" className="text-[13px] font-medium text-[#c0392b]">
              {error}
            </p>
          )}
          <div ref={endRef} />
        </div>

        <div className="flex flex-col gap-1.5 border-t border-fill px-4 pt-3 pb-[calc(12px+env(safe-area-inset-bottom))]">
          {canAsk ? (
            <div className="flex items-center gap-2">
              <input
                value={question}
                onChange={(event) => setQuestion(event.target.value)}
                // 한글 입력 중(조합 중) Enter는 글자를 확정하는 키라 보내지 않는다
                onKeyDown={(event) => {
                  if (event.key === "Enter" && !event.nativeEvent.isComposing) send(question);
                }}
                maxLength={MAX_QUESTION}
                placeholder="이 카드에 대해 물어보세요"
                aria-label="질문"
                className="h-11 min-w-0 flex-1 rounded-full bg-fill px-4 text-[15px] outline-none focus:ring-2 focus:ring-ink"
              />
              <button
                type="button"
                onClick={() => send(question)}
                disabled={waiting || !question.trim()}
                className="h-11 shrink-0 rounded-full bg-ink px-4 text-sm font-bold text-white disabled:opacity-40"
              >
                보내기
              </button>
            </div>
          ) : (
            <button
              type="button"
              onClick={onNeedLogin}
              className="h-11 rounded-full bg-ink text-sm font-bold text-white"
            >
              로그인하고 질문하기
            </button>
          )}
          {remaining !== null && <p className="px-2 text-[11px] text-muted">오늘 남은 질문 {remaining}번</p>}
        </div>
      </div>
    </div>
  );
}
