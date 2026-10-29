"use client";

import { useEffect, useMemo, useState } from "react";
import EventCard from "@/components/EventCard";
import FrontlineMap from "@/components/FrontlineMap";
import KindIcon from "@/components/KindIcon";
import StoriesPanel from "@/components/StoriesPanel";
import Timeline from "@/components/Timeline";
import { eventsBetween, fetchEvents, formatStartDate, prewarEvents, type WarEvent } from "@/lib/events";
import { COLORS } from "@/lib/map-style";
import { fetchSnapshots, formatDate, type Snapshot } from "@/lib/snapshots";

// 불러오기 전에도 같은 배열을 써서 useMemo가 매번 다시 계산하지 않게 한다
const NO_SNAPSHOTS: Snapshot[] = [];

type Load = { status: "loading" } | { status: "error" } | { status: "ready"; snapshots: Snapshot[] };

export default function WarMapApp() {
  const [load, setLoad] = useState<Load>({ status: "loading" });
  const [index, setIndex] = useState(0);
  const [events, setEvents] = useState<WarEvent[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  // 전쟁 속 이야기 화면. 스냅샷과 상관없어서 열고 닫아도 보던 스냅샷과 사건 카드는 그대로다
  const [storiesOpen, setStoriesOpen] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    fetchSnapshots(controller.signal)
      .then((snapshots) => setLoad({ status: "ready", snapshots }))
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          console.error(error);
          setLoad({ status: "error" });
        }
      });
    return () => controller.abort();
  }, []);

  // 사건은 따로 불러온다. 못 불러와도 전선 지도는 그대로 보여 준다
  useEffect(() => {
    const controller = new AbortController();
    fetchEvents(controller.signal)
      .then(setEvents)
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          console.error(error);
        }
      });
    return () => controller.abort();
  }, []);

  const snapshots = load.status === "ready" ? load.snapshots : NO_SNAPSHOTS;

  // 개전 앞의 사건이 있으면 타임라인 맨 앞에 "개전 전" 칸을 하나 더 둔다.
  // 지도는 개전 스냅샷(38선)을 그대로 쓴다. 기본(reviewed만)으로 켜서 그런 사건이 없으면 칸도 없다
  const prewar = useMemo(() => prewarEvents(events), [events]);
  const slots = useMemo(
    () => (prewar.length > 0 && snapshots.length > 0 ? [{ ...snapshots[0], title: "개전 전" }, ...snapshots] : snapshots),
    [prewar, snapshots],
  );
  const hasPrewar = slots.length > snapshots.length;
  const last = slots.length - 1;
  // 사건이 스냅샷보다 늦게 와서 맨 앞에 칸이 생기면, 이미 넘겨 둔 스냅샷을 그대로 보도록 한 칸 민다
  const [hadPrewar, setHadPrewar] = useState(hasPrewar);
  if (hadPrewar !== hasPrewar) {
    setHadPrewar(hasPrewar);
    if (index > 0) {
      setIndex(index + (hasPrewar ? 1 : -1));
    }
  }
  const current = slots[index];
  const isPrewar = hasPrewar && index === 0;

  // 직전 스냅샷 다음 날부터 지금 스냅샷 날짜까지 일어난 사건만 보여 준다. "개전 전" 칸은 개전 앞의 사건 전부
  const warIndex = hasPrewar ? index - 1 : index;
  const from = warIndex > 0 ? snapshots[warIndex - 1].snapshotDate : null;
  const to = current ? current.snapshotDate : null;
  const visibleEvents = useMemo(
    () => (isPrewar ? prewar : to ? eventsBetween(events, from, to) : []),
    [isPrewar, prewar, events, from, to],
  );
  const selected = visibleEvents.find((event) => event.id === selectedId) ?? null;
  // "개전 전" 칸의 날짜는 그 칸에서 가장 이른 사건의 시작일이다 (사건은 날짜순으로 온다)
  const heading = current ? (isPrewar ? formatStartDate(prewar[0]) : formatDate(current.snapshotDate)) : " ";

  // 스냅샷을 넘기면 열려 있던 사건 카드는 닫는다
  const goTo = (i: number) => {
    setIndex(i);
    setSelectedId(null);
  };

  // 키보드 ←/→로도 넘긴다 (PC에서 볼 때). Esc는 사건 카드를 닫는다.
  // 이야기 화면이 열려 있으면 키는 그 화면이 받는다
  useEffect(() => {
    if (storiesOpen) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "ArrowLeft" || event.key === "ArrowRight" || event.key === "Escape") {
        setSelectedId(null);
      }
      if (event.key === "ArrowLeft") setIndex((i) => Math.max(0, i - 1));
      if (event.key === "ArrowRight") setIndex((i) => Math.min(last, i + 1));
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [last, storiesOpen]);

  return (
    <main className="relative mx-auto flex h-dvh max-w-[480px] flex-col overflow-hidden">
      <header className="flex items-start justify-between px-5 pt-5 pb-1">
        <div className="flex flex-col gap-0.5">
          <div className="text-[13px] font-medium text-muted">Korean War</div>
          <h1 className="font-serif text-[26px] font-semibold tracking-tight tabular-nums">
            {heading}
          </h1>
          {/* 불러오는 동안에도 줄 높이를 잡아 둬서, 이름이 나타날 때 지도가 밀리지 않게 한다 */}
          <p className="text-lg font-semibold">{current ? current.title : " "}</p>
        </div>
        <div className="mt-0.5 flex items-center gap-1.5">
          {/* 시기와 상관없는 글이라, 스냅샷마다 바뀌는 날짜·제목 옆이 아니라 앱 이름 줄에 늘 둔다 */}
          <button
            type="button"
            onClick={() => setStoriesOpen(true)}
            className="inline-flex items-center gap-1 rounded-full border border-ink bg-white px-3 py-1.5 text-[13px] font-semibold"
          >
            <BookIcon />
            이야기
          </button>
          {current && (
            <div className="rounded-full border border-rule bg-white px-3 py-1.5 text-[13px] font-medium tabular-nums">
              {index + 1} / {slots.length}
            </div>
          )}
        </div>
      </header>

      <div className="relative min-h-0 flex-1">
        {current && (
          <FrontlineMap
            snapshot={current}
            events={visibleEvents}
            selectedId={selected?.id ?? null}
            onSelectEvent={setSelectedId}
            edgeLayout={isPrewar}
          />
        )}
        {load.status === "loading" && <Notice>전선 데이터를 불러오는 중…</Notice>}
        {load.status === "error" && <Notice>전선 데이터를 불러오지 못했습니다. 백엔드가 켜져 있는지 확인해 주세요.</Notice>}
        {load.status === "ready" && snapshots.length === 0 && <Notice>아직 스냅샷이 없습니다.</Notice>}
        {current && (
          <div className="pointer-events-none absolute bottom-3 left-4 flex flex-wrap gap-1.5">
            <Legend swatch={<span className="size-2.5 rounded-sm" style={{ background: COLORS.north }} />}>북측</Legend>
            <Legend swatch={<span className="size-2.5 rounded-sm" style={{ background: COLORS.south }} />}>남측</Legend>
            <Legend swatch={<span className="h-0.5 w-3.5" style={{ background: COLORS.frontLine }} />}>전선</Legend>
          </div>
        )}
      </div>

      {current && (
        <section
          className={`flex flex-col gap-4 rounded-t-[20px] border-t border-rule-soft bg-white px-5 pt-[18px] pb-[calc(24px+env(safe-area-inset-bottom))] ${
            selected ? "max-h-[65dvh] overflow-y-auto" : ""
          }`}
        >
          {selected ? (
            <EventCard event={selected} onClose={() => setSelectedId(null)} />
          ) : (
            <>
              {visibleEvents.length > 0 && <EventChips events={visibleEvents} onSelect={setSelectedId} />}

              <Timeline snapshots={snapshots} hasPrewar={hasPrewar} index={index} onSelect={goTo} />

              <div className="grid grid-cols-2 gap-3">
                <StepButton
                  direction="prev"
                  label={index > 0 ? slots[index - 1].title : "처음입니다"}
                  disabled={index === 0}
                  onClick={() => goTo(index - 1)}
                />
                <StepButton
                  direction="next"
                  label={index < last ? slots[index + 1].title : "마지막입니다"}
                  disabled={index === last}
                  onClick={() => goTo(index + 1)}
                />
              </div>
            </>
          )}
        </section>
      )}

      {storiesOpen && <StoriesPanel onClose={() => setStoriesOpen(false)} />}
    </main>
  );
}

// 펼친 책. 직접 그린 단순한 도형이다 (CLAUDE.md 4장: 남의 아이콘 세트는 쓰지 않는다)
function BookIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true">
      <path
        d="M7 3.5C5.6 2.6 3.7 2.3 1.5 2.5v8c2.2-.2 4.1.1 5.5 1 1.4-.9 3.3-1.2 5.5-1v-8c-2.2-.2-4.1.1-5.5 1Zm0 0v8"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.3"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function Notice({ children }: { children: React.ReactNode }) {
  return (
    <div className="absolute inset-0 flex items-center justify-center px-8 text-center text-sm text-muted">{children}</div>
  );
}

// 폰에서는 지도 위 작은 마커를 누르기 어려워서, 같은 사건을 칩으로도 보여 준다
function EventChips({ events, onSelect }: { events: WarEvent[]; onSelect: (id: string) => void }) {
  return (
    <div className="flex flex-col gap-1.5">
      <p className="text-xs font-medium text-muted">이 시기의 사건 {events.length}</p>
      <div className="-mx-5 flex gap-1.5 overflow-x-auto px-5">
        {events.map((event) => (
          <button
            key={event.id}
            type="button"
            onClick={() => onSelect(event.id)}
            className="flex h-11 shrink-0 items-center gap-1.5 rounded-full border border-rule bg-white pr-3.5 pl-3 text-sm font-medium"
          >
            <KindIcon kind={event.kind} />
            {event.title}
          </button>
        ))}
      </div>
    </div>
  );
}

function Legend({ swatch, children }: { swatch: React.ReactNode; children: React.ReactNode }) {
  return (
    <span className="inline-flex items-center gap-1.5 rounded-full bg-white px-2.5 py-1 text-xs font-medium">
      {swatch}
      {children}
    </span>
  );
}

function StepButton({
  direction,
  label,
  disabled,
  onClick,
}: {
  direction: "prev" | "next";
  label: string;
  disabled: boolean;
  onClick: () => void;
}) {
  const next = direction === "next";
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={`${next ? "다음" : "이전"} 스냅샷 ${label}`}
      className={`flex h-14 flex-col justify-center gap-0.5 rounded-[14px] border px-3.5 disabled:opacity-45 ${
        next ? "items-end border-ink bg-ink text-white" : "items-start border-rule bg-white text-ink"
      }`}
    >
      <span className={`inline-flex items-center gap-1 text-xs ${next ? "text-rule" : "text-muted"}`}>
        {!next && <Chevron flip />}
        {next ? "다음" : "이전"}
        {next && <Chevron />}
      </span>
      <span className="max-w-full truncate text-[15px] font-semibold tabular-nums">{label}</span>
    </button>
  );
}

function Chevron({ flip = false }: { flip?: boolean }) {
  return (
    <svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true">
      <path
        d={flip ? "M7.5 2.5 4 6l3.5 3.5" : "M4.5 2.5 8 6l-3.5 3.5"}
        fill="none"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}
