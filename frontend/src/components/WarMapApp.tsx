"use client";

import { useEffect, useMemo, useState } from "react";
import EventCard from "@/components/EventCard";
import FrontlineMap from "@/components/FrontlineMap";
import Timeline from "@/components/Timeline";
import { eventsBetween, fetchEvents, type WarEvent } from "@/lib/events";
import { COLORS } from "@/lib/map-style";
import { fetchSnapshots, formatDate, type Snapshot } from "@/lib/snapshots";

type Load = { status: "loading" } | { status: "error" } | { status: "ready"; snapshots: Snapshot[] };

export default function WarMapApp() {
  const [load, setLoad] = useState<Load>({ status: "loading" });
  const [index, setIndex] = useState(0);
  const [events, setEvents] = useState<WarEvent[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);

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

  const snapshots = load.status === "ready" ? load.snapshots : [];
  const last = snapshots.length - 1;
  const current = snapshots[index];

  // 직전 스냅샷 다음 날부터 지금 스냅샷 날짜까지 일어난 사건만 보여 준다
  const from = index > 0 ? snapshots[index - 1].snapshotDate : null;
  const to = current ? current.snapshotDate : null;
  const visibleEvents = useMemo(() => (to ? eventsBetween(events, from, to) : []), [events, from, to]);
  const selected = visibleEvents.find((event) => event.id === selectedId) ?? null;

  // 스냅샷을 넘기면 열려 있던 사건 카드는 닫는다
  const goTo = (i: number) => {
    setIndex(i);
    setSelectedId(null);
  };

  // 키보드 ←/→로도 넘긴다 (PC에서 볼 때). Esc는 사건 카드를 닫는다
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "ArrowLeft" || event.key === "ArrowRight" || event.key === "Escape") {
        setSelectedId(null);
      }
      if (event.key === "ArrowLeft") setIndex((i) => Math.max(0, i - 1));
      if (event.key === "ArrowRight") setIndex((i) => Math.min(last, i + 1));
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [last]);

  return (
    <main className="mx-auto flex h-dvh max-w-[480px] flex-col overflow-hidden">
      <header className="flex items-start justify-between px-5 pt-5 pb-1">
        <div className="flex flex-col gap-0.5">
          <div className="text-[13px] font-medium text-muted">Korean War</div>
          <h1 className="font-serif text-[26px] font-semibold tracking-tight tabular-nums">
            {current ? formatDate(current.snapshotDate) : " "}
          </h1>
          {/* 불러오는 동안에도 줄 높이를 잡아 둬서, 이름이 나타날 때 지도가 밀리지 않게 한다 */}
          <p className="text-lg font-semibold">{current ? current.title : " "}</p>
        </div>
        {current && (
          <div className="mt-0.5 rounded-full border border-rule bg-white px-3 py-1.5 text-[13px] font-medium tabular-nums">
            {index + 1} / {snapshots.length}
          </div>
        )}
      </header>

      <div className="relative min-h-0 flex-1">
        {current && (
          <FrontlineMap
            snapshot={current}
            events={visibleEvents}
            selectedId={selected?.id ?? null}
            onSelectEvent={setSelectedId}
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

              <div className="flex flex-col gap-1 text-xs text-muted">
                <p>전선 근거 · {current.sourceMap}</p>
                <p>해안선·국경 · Natural Earth</p>
              </div>

              <Timeline snapshots={snapshots} index={index} onSelect={goTo} />

              <div className="grid grid-cols-2 gap-3">
                <StepButton
                  direction="prev"
                  label={index > 0 ? snapshots[index - 1].title : "처음입니다"}
                  disabled={index === 0}
                  onClick={() => goTo(index - 1)}
                />
                <StepButton
                  direction="next"
                  label={index < last ? snapshots[index + 1].title : "마지막입니다"}
                  disabled={index === last}
                  onClick={() => goTo(index + 1)}
                />
              </div>
            </>
          )}
        </section>
      )}
    </main>
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
            className="h-11 shrink-0 rounded-full border border-rule bg-white px-3.5 text-sm font-medium"
          >
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
