import { WAR_END, WAR_START, formatDate, positionInWar, type Snapshot } from "@/lib/snapshots";

const YEARS = ["1951-01-01", "1952-01-01", "1953-01-01"];

function percent(iso: string): string {
  return `${(positionInWar(iso) * 100).toFixed(2)}%`;
}

function shortDate(iso: string): string {
  return formatDate(iso).replaceAll(" ", "").replace(/\.$/, "");
}

// 전쟁 기간 전체를 한 줄로 놓고, 스냅샷 날짜를 점으로 찍는다. 점을 눌러 그 날짜로 갈 수 있다.
// "개전 전" 칸이 있으면(hasPrewar) 줄 왼쪽에 따로 점 하나를 둔다. 이때 index 0이 "개전 전" 칸이다
export default function Timeline({
  snapshots,
  hasPrewar,
  index,
  onSelect,
}: {
  snapshots: Snapshot[];
  hasPrewar: boolean;
  index: number;
  onSelect: (index: number) => void;
}) {
  const offset = hasPrewar ? 1 : 0;
  const warIndex = index - offset; // 전쟁 기간 줄에서의 위치. "개전 전" 칸이면 -1
  const line = (
    <div className="flex min-w-0 flex-1 flex-col gap-1">
      <div className="flex justify-between text-[11px] text-muted tabular-nums">
        <span>{shortDate(WAR_START)} 개전</span>
        <span>{shortDate(WAR_END)} 정전</span>
      </div>

      <div className="relative h-7">
        <div className="absolute inset-x-0 top-2.5 h-2 rounded-full bg-fill" />
        <div
          className="absolute left-0 top-2.5 h-2 rounded-full bg-ink transition-[width] duration-700"
          style={{ width: warIndex < 0 ? "0%" : percent(snapshots[warIndex].snapshotDate) }}
        />
        {snapshots.map((snapshot, i) => (
          <button
            key={snapshot.snapshotDate}
            type="button"
            onClick={() => onSelect(i + offset)}
            aria-label={`${formatDate(snapshot.snapshotDate)} ${snapshot.title} 보기`}
            aria-current={i === warIndex ? "step" : undefined}
            className="absolute top-3.5 -ml-3 -mt-3 flex size-6 items-center justify-center"
            style={{ left: percent(snapshot.snapshotDate) }}
          >
            <span className={dotClass(i === warIndex, i < warIndex ? "bg-white" : "bg-dot")} />
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

  if (!hasPrewar) {
    return line;
  }
  // 위 글자 줄·점 줄·연도 줄의 높이를 전쟁 기간 줄과 맞춘다
  return (
    <div className="flex gap-2">
      <div className="flex w-9 shrink-0 flex-col gap-1">
        <div className="h-[16.5px]" />
        <div className="relative h-7">
          <button
            type="button"
            onClick={() => onSelect(0)}
            aria-label="개전 전 보기"
            aria-current={index === 0 ? "step" : undefined}
            className="absolute top-3.5 left-1/2 -ml-3 -mt-3 flex size-6 items-center justify-center"
          >
            <span className={dotClass(index === 0, index > 0 ? "bg-ink" : "bg-dot")} />
          </button>
        </div>
        <span className="h-3.5 text-center text-[11px] leading-[14px] whitespace-nowrap text-muted">개전 전</span>
      </div>
      {line}
    </div>
  );
}

// 지금 칸은 흰 손잡이, 나머지는 작은 점. 점 색은 뒤에 깔린 줄에 맞춰 부르는 쪽이 정한다
// (지나온 칸은 검정 줄 위라 흰 점, "개전 전" 칸은 줄 밖이라 검정 점)
function dotClass(current: boolean, color: string): string {
  return current ? "size-[22px] rounded-full bg-white shadow-[0_1px_6px_rgba(26,29,33,0.3)]" : `size-1.5 rounded-full ${color}`;
}
