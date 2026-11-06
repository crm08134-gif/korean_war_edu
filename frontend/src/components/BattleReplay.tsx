"use client";

import { formatKeyDate, shortKeyLabel, type Battle } from "@/lib/battles";

// 전투 재생 중 아래 패널 자리에 뜨는 시트 (docs/battle-replay.md 1장, 데모에서 확정한 모양):
// 날짜와 "N / 전체", 그 날짜의 설명, 키프레임 버튼, 재생·일시 정지, 위치 막대, 근거 지도
export default function BattleReplay({
  battle,
  t,
  nearest,
  playing,
  onPick,
  onTogglePlay,
  onScrub,
}: {
  battle: Battle;
  t: number; // 0 ~ 키프레임 수 - 1
  nearest: number;
  playing: boolean;
  onPick: (index: number) => void;
  onTogglePlay: () => void;
  onScrub: (t: number) => void;
}) {
  const keyframes = battle.keyframes;
  const current = keyframes[nearest];
  const steps = (keyframes.length - 1) * 100;
  // 키프레임이 모두 같은 날이면(하루짜리 전투) 버튼에 시각만 적는다
  let sameDay = true;
  for (const k of keyframes) {
    if (k.date.slice(0, 10) !== keyframes[0].date.slice(0, 10)) sameDay = false;
  }

  return (
    <section
      aria-label="전투 재생 조작"
      className="flex flex-col gap-3.5 rounded-t-[28px] bg-white px-5 pt-5 pb-[calc(18px+env(safe-area-inset-bottom))] shadow-[0_-8px_30px_rgba(26,29,33,0.12)]"
    >
      <div className="flex items-baseline justify-between">
        <p className="text-2xl font-extrabold tracking-tight tabular-nums">{formatKeyDate(current.date)}</p>
        <p className="text-[13px] font-bold text-muted tabular-nums">
          {nearest + 1} / {keyframes.length}
        </p>
      </div>
      {/* 날짜가 바뀌면 화면 읽기 프로그램이 새 설명을 읽는다 */}
      <p aria-live="polite" className="min-h-12 text-[15px] leading-relaxed">
        {current.caption}
      </p>

      <div
        className="grid gap-2"
        style={{
          gridTemplateColumns: `repeat(${keyframes.length}, minmax(0, 1fr))`,
        }}
      >
        {keyframes.map((k, i) => (
          <button
            key={k.date}
            type="button"
            onClick={() => onPick(i)}
            aria-pressed={i === nearest}
            className={`h-11 rounded-xl text-sm font-bold tabular-nums ${
              i === nearest ? "bg-ink text-white" : "bg-fill text-ink"
            }`}
          >
            {shortKeyLabel(k.date, sameDay)}
          </button>
        ))}
      </div>

      <div className="flex items-center gap-3">
        <button
          type="button"
          onClick={onTogglePlay}
          aria-label={playing ? "일시 정지" : "재생"}
          className="flex size-12 shrink-0 items-center justify-center rounded-full bg-ink text-white"
        >
          {playing ? (
            <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true">
              <path d="M4 3h3v10H4zM9 3h3v10H9z" fill="currentColor" />
            </svg>
          ) : (
            <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true">
              <path d="M4.5 2.5v11l9-5.5z" fill="currentColor" />
            </svg>
          )}
        </button>
        <input
          type="range"
          min={0}
          max={steps}
          step={1}
          value={Math.round(t * 100)}
          onChange={(event) => onScrub(Number(event.target.value) / 100)}
          aria-label="재생 위치"
          className="h-11 min-w-0 flex-1 accent-ink"
        />
      </div>

      <div className="flex flex-col gap-1 rounded-2xl bg-fill px-3.5 py-3">
        <p className="text-xs font-bold">대략적 재현 · 근거 지도</p>
        <ul className="flex flex-col gap-0.5 text-[11px] leading-relaxed text-muted">
          {battle.maps.map((map) => (
            <li key={map.title}>
              {map.title}
              {map.note && ` (${map.note})`}
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
