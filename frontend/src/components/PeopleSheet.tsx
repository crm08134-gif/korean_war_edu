"use client";

import { useEffect, useRef, useState } from "react";
import { CloseIcon, PhotoCredit, SourceLine } from "@/components/EventCard";
import type { EventPerson } from "@/lib/events";
import { COUNTRY_LABEL, SIDE_COLOR, SIDE_LABEL, sortPeople } from "@/lib/people";
import { applyVote, fetchVotes, putVote, type VoteCount, type VoteValue } from "@/lib/votes";

// 사건 카드의 "관련 인물" 버튼으로 여는 시트. 카드 위를 덮고, 닫으면 읽던 카드로 돌아간다
export default function PeopleSheet({
  eventId,
  title,
  people,
  onClose,
}: {
  eventId: string;
  title: string;
  people: EventPerson[];
  onClose: () => void;
}) {
  const headingRef = useRef<HTMLHeadingElement>(null);
  // 인물별 좋아요·싫어요 수. 불러오기 전이나 실패했으면 비어 있고, 버튼은 숫자 없이 보인다
  const [votes, setVotes] = useState<Map<string, VoteCount>>(new Map());

  useEffect(() => {
    const controller = new AbortController();
    fetchVotes(eventId, controller.signal)
      .then((counts) => setVotes(new Map(counts.map((c) => [c.personId, c]))))
      .catch((error: unknown) => {
        if (!controller.signal.aborted) console.error(error);
      });
    return () => controller.abort();
  }, [eventId]);

  // 누르면 숫자를 먼저 바꿔 보여 주고, 서버가 준 수로 맞춘다. 실패하면 누르기 전으로 되돌린다
  const vote = (personId: string, pressed: 1 | -1) => {
    const before = votes;
    const current = before.get(personId) ?? { personId, up: 0, down: 0, mine: 0 };
    const value: VoteValue = current.mine === pressed ? 0 : pressed;
    setVotes(new Map(before).set(personId, applyVote(current, value)));
    putVote(eventId, personId, value)
      .then((counts) => setVotes(new Map(counts.map((c) => [c.personId, c]))))
      .catch((error: unknown) => {
        console.error(error);
        setVotes(before);
      });
  };

  useEffect(() => {
    headingRef.current?.focus();
  }, []);

  // Esc와 ←/→는 이 시트가 먼저 받는다. 지도 화면(WarMapApp)까지 가면 사건 카드가 닫히거나 스냅샷이 넘어간다
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
    <div className="absolute inset-0 z-20 flex flex-col justify-end bg-ink/30" onClick={onClose}>
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="people-sheet-title"
        onClick={(event) => event.stopPropagation()}
        className="flex max-h-[88%] flex-col rounded-t-3xl bg-white"
      >
        <div className="flex items-center justify-between gap-3 px-5 pt-5 pb-3">
          <div className="flex min-w-0 flex-col">
            <p className="truncate text-[13px] font-medium text-muted">{title}</p>
            <h2
              id="people-sheet-title"
              ref={headingRef}
              tabIndex={-1}
              className="text-xl font-extrabold tracking-tight outline-none"
            >
              관련 인물
            </h2>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="관련 인물 닫기"
            className="flex size-11 shrink-0 items-center justify-center rounded-full bg-fill"
          >
            <CloseIcon />
          </button>
        </div>
        <ul className="flex min-h-0 flex-col gap-3 overflow-y-auto px-5 pb-[calc(24px+env(safe-area-inset-bottom))]">
          {sortPeople(people).map((person) => (
            <li key={person.id}>
              <PersonCard person={person} count={votes.get(person.id)} onVote={(v) => vote(person.id, v)} />
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}

function PersonCard({
  person,
  count,
  onVote,
}: {
  person: EventPerson;
  count: VoteCount | undefined;
  onVote: (value: 1 | -1) => void;
}) {
  return (
    <article className="flex flex-col gap-3 rounded-2xl bg-fill p-4">
      <div className="flex items-center gap-3.5">
        <Portrait person={person} />
        <div className="flex min-w-0 flex-col gap-0.5">
          <h3 className="flex items-center gap-1.5 text-[17px] font-extrabold tracking-tight">
            <span className="truncate">{person.name}</span>
            <Flag country={person.country} />
          </h3>
          <p className="truncate text-xs text-muted">{person.nameOriginal}</p>
          <p className="text-[13px] font-medium">{person.role}</p>
          {/* 색만으로 진영을 알리지 않도록 글자로도 적는다 */}
          <p className="text-[11px] font-bold" style={{ color: SIDE_COLOR[person.side] }}>
            {SIDE_LABEL[person.side]}
          </p>
        </div>
      </div>

      <p className="text-[15px] leading-[1.75]">{person.description}</p>

      <div className="flex gap-2">
        <VoteButton kind="up" name={person.name} count={count?.up} pressed={count?.mine === 1} onClick={() => onVote(1)} />
        <VoteButton kind="down" name={person.name} count={count?.down} pressed={count?.mine === -1} onClick={() => onVote(-1)} />
      </div>

      <footer className="flex flex-col gap-0.5 text-xs leading-relaxed text-muted">
        {person.photo ? <PhotoCredit meta={person.photo} /> : <span>저작권이 확인된 사진이 없습니다</span>}
        {person.sources.map((source, i) => (
          <span key={i}>
            출처: <SourceLine source={source} />
          </span>
        ))}
      </footer>
    </article>
  );
}

// 둥근 사진. 진영색 테두리를 흐리게 번지게 한다. 사진이 없으면 이름 첫 글자를 넣은 원
function Portrait({ person }: { person: EventPerson }) {
  const color = SIDE_COLOR[person.side];
  const ring = { boxShadow: `0 0 0 3px ${color}, 0 0 14px 2px ${color}` };
  if (!person.photo) {
    return (
      <div
        aria-hidden="true"
        style={ring}
        className="flex size-16 shrink-0 items-center justify-center rounded-full bg-white text-2xl font-extrabold"
      >
        <span style={{ color }}>{person.name.slice(0, 1)}</span>
      </div>
    );
  }
  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={`/photos/${encodeURIComponent(person.photo.file)}`}
      alt={person.name}
      loading="lazy"
      style={ring}
      className="size-16 shrink-0 rounded-full bg-sea object-cover object-top"
    />
  );
}

// 당시 국기. 그림 파일이 아직 없으면 감춘다
function Flag({ country }: { country: string }) {
  const [missing, setMissing] = useState(false);
  if (missing) return null;
  const label = COUNTRY_LABEL[country] ?? country;
  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={`/flags/${country}.svg`}
      alt={label}
      title={label}
      onError={() => setMissing(true)}
      className="h-3.5 w-auto shrink-0 rounded-[2px] ring-1 ring-rule-soft"
    />
  );
}

// 엄지 위·아래 버튼. 누른 버튼은 진하게 칠하고, 다시 누르면 취소된다
function VoteButton({
  kind,
  name,
  count,
  pressed,
  onClick,
}: {
  kind: "up" | "down";
  name: string;
  count: number | undefined;
  pressed: boolean;
  onClick: () => void;
}) {
  const label = kind === "up" ? "좋아요" : "싫어요";
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={pressed}
      aria-label={`${name} ${label}${count === undefined ? "" : ` ${count}`}`}
      className={`flex h-9 items-center gap-1.5 rounded-full px-3.5 text-sm font-bold tabular-nums ${
        pressed ? "bg-ink text-white" : "bg-white text-ink ring-1 ring-rule-soft"
      }`}
    >
      <ThumbIcon down={kind === "down"} />
      {count ?? ""}
    </button>
  );
}

function ThumbIcon({ down }: { down: boolean }) {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true" style={down ? { transform: "scaleY(-1)" } : undefined}>
      <path
        d="M5 7.2 7.6 2.3c.8 0 1.5.7 1.4 1.6L8.6 6.3h3.7c.9 0 1.6.9 1.4 1.8l-1 4.6c-.2.7-.8 1.3-1.6 1.3H5V7.2ZM2 7.2h3V14H2z"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinejoin="round"
      />
    </svg>
  );
}
