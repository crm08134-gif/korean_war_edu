import type { EventKind } from "@/lib/events";
import { KIND_ICON } from "@/lib/event-kinds";

// 사건 종류 아이콘 (칩, 카드). 지도 마커는 같은 모양을 kindIconSvg()로 그린다
export default function KindIcon({ kind, size = 16 }: { kind: EventKind; size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 16 16"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {KIND_ICON[kind].map((d) => (
        <path key={d} d={d} />
      ))}
    </svg>
  );
}
