"use client";

import { useEffect, useRef, useState } from "react";
import { CloseIcon } from "@/components/EventCard";
import { DRAFT_NOTICE, POLICIES, type PolicyId } from "@/lib/policies";

const ORDER: PolicyId[] = ["rules", "terms", "privacy"];

// 운영 정책·이용약관·개인정보 처리방침. 게시판 위쪽 안내와 로그인 시트에서 연다.
// 다른 시트(로그인, 게시판) 위에 뜬다
export default function PolicySheet({ initial, onClose }: { initial: PolicyId; onClose: () => void }) {
  const [current, setCurrent] = useState<PolicyId>(initial);
  const headingRef = useRef<HTMLHeadingElement>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const policy = POLICIES[current];

  useEffect(() => {
    headingRef.current?.focus();
    scrollRef.current?.scrollTo({ top: 0 });
  }, [current]);

  // Esc는 이 화면이 먼저 받는다 (아래 게시판이나 지도까지 가지 않게)
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key !== "Escape" && event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
      event.stopPropagation();
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey, { capture: true });
    return () => window.removeEventListener("keydown", onKey, { capture: true });
  }, [onClose]);

  return (
    <div role="dialog" aria-modal="true" aria-labelledby="policy-title" className="absolute inset-0 z-40 flex flex-col bg-white">
      <div className="flex flex-col gap-3 px-5 pt-5 pb-3">
        <div className="flex items-center justify-between gap-3">
          <h2
            id="policy-title"
            ref={headingRef}
            tabIndex={-1}
            className="text-[22px] font-extrabold tracking-tight outline-none"
          >
            {policy.title}
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label={`${policy.title} 닫기`}
            className="-mr-2.5 flex size-11 shrink-0 items-center justify-center rounded-full text-muted"
          >
            <CloseIcon />
          </button>
        </div>
        <div role="tablist" aria-label="문서 고르기" className="flex rounded-full bg-fill p-1">
          {ORDER.map((id) => (
            <button
              key={id}
              type="button"
              role="tab"
              aria-selected={current === id}
              onClick={() => setCurrent(id)}
              className={`h-9 flex-1 rounded-full text-[13px] font-bold ${current === id ? "bg-white shadow-sm" : "text-muted"}`}
            >
              {POLICIES[id].title}
            </button>
          ))}
        </div>
      </div>

      <div
        ref={scrollRef}
        className="min-h-0 flex-1 overflow-y-auto px-5 pt-2 pb-[calc(24px+env(safe-area-inset-bottom))]"
      >
        <p className="rounded-2xl bg-fill px-4 py-3 text-xs leading-relaxed text-muted">
          {DRAFT_NOTICE} · {policy.updated}
        </p>
        {policy.sections.map((section) => (
          <section key={section.heading} className="mt-5 flex flex-col gap-2">
            <h3 className="text-[15px] font-bold">{section.heading}</h3>
            {section.paragraphs?.map((text) => (
              <p key={text} className="text-sm leading-relaxed">
                {text}
              </p>
            ))}
            {section.items && (
              <ul className="flex list-disc flex-col gap-1.5 pl-5 text-sm leading-relaxed">
                {section.items.map((text) => (
                  <li key={text}>{text}</li>
                ))}
              </ul>
            )}
          </section>
        ))}
      </div>
    </div>
  );
}
