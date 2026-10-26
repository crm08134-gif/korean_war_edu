"use client";

import { useEffect, useRef } from "react";
import { formatEventDate, type EventSource, type WarEvent } from "@/lib/events";

// 하단 패널 자리에 들어가는 사건 카드. 닫으면 다시 타임라인 패널로 돌아간다
export default function EventCard({ event, onClose }: { event: WarEvent; onClose: () => void }) {
  const headingRef = useRef<HTMLHeadingElement>(null);

  // 칩이나 마커를 눌러 열면 읽기 위치(포커스)를 카드 제목으로 옮긴다
  useEffect(() => {
    headingRef.current?.focus();
  }, [event.id]);

  const claims = event.claims ?? [];

  return (
    <article aria-labelledby="event-card-title" className="flex flex-col gap-3">
      <div className="flex items-start justify-between gap-3">
        <div className="flex min-w-0 flex-col gap-1">
          <p className="text-xs text-muted tabular-nums">
            {formatEventDate(event)} · {event.placeName}
          </p>
          <h2
            id="event-card-title"
            ref={headingRef}
            tabIndex={-1}
            className="font-serif text-xl font-semibold tracking-tight outline-none"
          >
            {event.title}
          </h2>
        </div>
        <button
          type="button"
          onClick={onClose}
          aria-label="사건 카드 닫기"
          className="-mt-1.5 -mr-2.5 flex size-11 shrink-0 items-center justify-center rounded-full text-muted"
        >
          <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true">
            <path d="M4 4l8 8M12 4l-8 8" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
          </svg>
        </button>
      </div>

      <p className="text-[15px] leading-snug font-semibold">{event.hook}</p>
      <p className="text-[15px] leading-relaxed">{event.body}</p>

      {claims.length > 0 && (
        <details className="group rounded-[14px] border border-rule">
          <summary className="flex h-11 cursor-pointer list-none items-center justify-between px-3.5 text-sm font-semibold [&::-webkit-details-marker]:hidden">
            근거 보기 · 주장 {claims.length}개
            <svg
              width="12"
              height="12"
              viewBox="0 0 12 12"
              aria-hidden="true"
              className="transition-transform group-open:rotate-90"
            >
              <path
                d="M4.5 2.5 8 6l-3.5 3.5"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.6"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
          </summary>
          <ol className="flex flex-col gap-3.5 border-t border-rule-soft px-3.5 py-3">
            {claims.map((claim, i) => (
              <li key={i} className="flex flex-col gap-1.5">
                <p className="text-sm">{claim.text}</p>
                {claim.evidence.map((evidence, j) => (
                  <figure key={j} className="rounded-[10px] bg-sea px-2.5 py-2 text-xs leading-relaxed">
                    {evidence.quote && <blockquote>“{evidence.quote}”</blockquote>}
                    <figcaption className="mt-1 text-muted">
                      <SourceName source={evidence.source} /> · {evidence.locator} · {evidence.source.license}
                    </figcaption>
                  </figure>
                ))}
              </li>
            ))}
          </ol>
        </details>
      )}
    </article>
  );
}

function SourceName({ source }: { source: EventSource }) {
  const name = source.author ? `${source.author}, ${source.title}` : source.title;
  if (!source.url) {
    return <>{name}</>;
  }
  return (
    <a href={source.url} target="_blank" rel="noreferrer" className="underline underline-offset-2">
      {name}
    </a>
  );
}
