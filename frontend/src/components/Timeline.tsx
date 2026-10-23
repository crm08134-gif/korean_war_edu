import { WAR_END, WAR_START, formatDate, positionInWar, type Snapshot } from "@/lib/snapshots";

const YEARS = ["1951-01-01", "1952-01-01", "1953-01-01"];

function percent(iso: string): string {
  return `${(positionInWar(iso) * 100).toFixed(2)}%`;
}

function shortDate(iso: string): string {
  return formatDate(iso).replaceAll(" ", "").replace(/\.$/, "");
}

// 전쟁 기간 전체를 한 줄로 놓고, 스냅샷 날짜를 점으로 찍는다. 점을 눌러 그 날짜로 갈 수 있다
export default function Timeline({
  snapshots,
  index,
  onSelect,
}: {
  snapshots: Snapshot[];
  index: number;
  onSelect: (index: number) => void;
}) {
  return (
    <div className="flex flex-col gap-1">
      <div className="flex justify-between text-[11px] text-muted tabular-nums">
        <span>{shortDate(WAR_START)} 개전</span>
        <span>{shortDate(WAR_END)} 정전</span>
      </div>

      <div className="relative h-7">
        <div className="absolute inset-x-0 top-[13px] h-0.5 rounded-full bg-rule-soft" />
        <div
          className="absolute left-0 top-[13px] h-0.5 rounded-full bg-ink transition-[width] duration-700"
          style={{ width: percent(snapshots[index].snapshotDate) }}
        />
        {YEARS.map((year) => (
          <div key={year} className="absolute top-[7px] h-3.5 w-px bg-rule" style={{ left: percent(year) }} />
        ))}
        {snapshots.map((snapshot, i) => (
          <button
            key={snapshot.snapshotDate}
            type="button"
            onClick={() => onSelect(i)}
            aria-label={`${formatDate(snapshot.snapshotDate)} ${snapshot.title} 보기`}
            aria-current={i === index ? "step" : undefined}
            className="absolute top-3.5 -ml-3 -mt-3 flex size-6 items-center justify-center"
            style={{ left: percent(snapshot.snapshotDate) }}
          >
            <span
              className={
                i === index
                  ? "size-4 rounded-full border-[3px] border-ink bg-white"
                  : i < index
                    ? "size-1.5 rounded-full bg-ink"
                    : "size-1.5 rounded-full bg-dot"
              }
            />
          </button>
        ))}
      </div>

      <div className="relative h-3.5 text-[11px] text-muted">
        {YEARS.map((year) => (
          <span key={year} className="absolute -translate-x-1/2" style={{ left: percent(year) }}>
            {year.slice(0, 4)}
          </span>
        ))}
      </div>
    </div>
  );
}
