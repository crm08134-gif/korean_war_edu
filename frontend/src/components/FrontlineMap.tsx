"use client";

import type { GeoJSONSource, Map as MapLibreMap, Marker as MapLibreMarker } from "maplibre-gl";
import { useEffect, useRef, useState } from "react";
import KindIcon from "@/components/KindIcon";
import { KIND_LABEL, kindIconSvg } from "@/lib/event-kinds";
import type { Region, WarEvent } from "@/lib/events";
import { easeInOutCubic, interpolateLine } from "@/lib/geometry";
import {
  EMPTY_DATA,
  KOREA_BOUNDS,
  MAP_STYLE,
  SAFE_BOUNDS,
  VIEW_LIMIT_BOUNDS,
  extraData,
  warData,
} from "@/lib/map-style";
import type { Position, Snapshot } from "@/lib/snapshots";

// 전선이 다음 스냅샷으로 옮겨 가는 시간
const MOVE_MS = 1200;

// 처음 화면에서 한반도 둘레에 두는 여백. 아래쪽은 범례 자리만큼 비운다
const PADDING = { top: 12, right: 12, bottom: 44, left: 12 };
// "개전 전" 칸에서는 위·양옆에 가장자리 마커 자리(EdgeMarkers)를 더 비운다.
// fitBounds가 한반도 범위를 이 여백 안쪽에 맞추므로, 가장자리 띠가 이보다 좁으면 한반도와 겹치지 않는다
const EDGE_PADDING = { top: 60, right: 96, bottom: 44, left: 96 };

// MapLibre의 줌 0 세계 크기(px). 줌이 1 오르면 두 배가 된다
const WORLD_PX = 512;

// 경위도 ↔ 웹 메르카토르 좌표(0~1)
const mercX = (lon: number) => (lon + 180) / 360;
const mercY = (lat: number) => {
  const sin = Math.sin((lat * Math.PI) / 180);
  return 0.5 - Math.log((1 + sin) / (1 - sin)) / (4 * Math.PI);
};
const lonOf = (x: number) => x * 360 - 180;
const latOf = (y: number) => (Math.atan(Math.sinh(Math.PI * (1 - 2 * y))) * 180) / Math.PI;

// 가장 많이 축소했을 때 VIEW_LIMIT_BOUNDS가 지도 칸에 딱 들어오게 최소 줌과 끌 수 있는 범위를 정한다.
// 지도 칸 크기가 바뀔 때마다 다시 부른다
function applyViewLimit(map: MapLibreMap) {
  const camera = map.cameraForBounds(VIEW_LIMIT_BOUNDS);
  if (camera?.zoom === undefined) {
    return;
  }
  // 그 줌에서 경계의 가운데를 볼 때 화면에 보이는 범위. 지도 칸이 경계보다 길쭉하면 한쪽이 더 보인다
  const { width, height } = map.getContainer().getBoundingClientRect();
  const [[west, south], [east, north]] = VIEW_LIMIT_BOUNDS;
  const cx = (mercX(west) + mercX(east)) / 2;
  const cy = (mercY(south) + mercY(north)) / 2;
  const world = WORLD_PX * 2 ** camera.zoom;
  const halfX = width / 2 / world + 1e-6;
  const halfY = height / 2 / world + 1e-6;
  // 끌어도 이 범위 밖으로 나가지 않는다. 다만 바다 덮개가 덮는 안전 범위를 넘지 않게 자른다
  // (아주 넓은 창에서는 잘린 만큼 최소 줌이 조금 커진다)
  map.setMaxBounds([
    [Math.max(lonOf(cx - halfX), SAFE_BOUNDS.west), Math.max(latOf(cy + halfY), SAFE_BOUNDS.south)],
    [Math.min(lonOf(cx + halfX), SAFE_BOUNDS.east), Math.min(latOf(cy - halfY), SAFE_BOUNDS.north)],
  ]);
  map.setMinZoom(camera.zoom);
}

function source(map: MapLibreMap, id: string): GeoJSONSource {
  return map.getSource<GeoJSONSource>(id)!;
}

// 전선 하나로 양측 색과 전선을 그린다
function drawLine(map: MapLibreMap, line: Position[]) {
  void source(map, "war").setData(warData(line));
}

// 사건 마커 하나. 몇 개뿐이라 지도 레이어 대신 HTML로 그린다.
// 지도 글꼴 없이 한글 이름을 쓸 수 있고, 버튼이라 누를 수 있으며, 늘 지도 맨 위에 놓인다
function markerElement(event: WarEvent, selected: boolean, onSelect: (id: string) => void): HTMLButtonElement {
  const element = document.createElement("button");
  element.type = "button";
  element.setAttribute("aria-label", `${event.title}(${KIND_LABEL[event.kind]}) 사건 보기`);
  element.setAttribute("aria-pressed", String(selected));
  element.className = "flex items-center gap-1";

  // 종류 아이콘을 담은 원. 흰 바탕에 검정 선이라 북 빨강·남 파랑 어느 쪽 위에서도 보이고 두 색과 겹치지 않는다.
  // 고른 사건은 색을 뒤집는다 (검정 바탕에 흰 선)
  const badge = document.createElement("span");
  badge.className = `flex size-[26px] items-center justify-center rounded-full border-2 border-ink ${
    selected ? "bg-ink text-white" : "bg-white text-ink"
  }`;
  badge.innerHTML = kindIconSvg(event.kind, 16);
  const label = document.createElement("span");
  label.className = "rounded-md bg-white/90 px-1.5 py-0.5 text-xs font-semibold whitespace-nowrap text-ink";
  label.textContent = event.title;
  element.append(badge, label);

  element.addEventListener("click", (e) => {
    e.stopPropagation();
    onSelect(event.id);
  });
  return element;
}

export default function FrontlineMap({
  snapshot,
  events,
  selectedId,
  onSelectEvent,
  edgeLayout,
}: {
  snapshot: Snapshot;
  events: WarEvent[];
  selectedId: string | null;
  onSelectEvent: (id: string) => void;
  edgeLayout: boolean; // "개전 전" 칸: 가장자리 마커 자리를 비우고 한반도를 안쪽으로 줄인다
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<MapLibreMap | null>(null);
  const markerClassRef = useRef<typeof MapLibreMarker | null>(null);
  const onSelectRef = useRef(onSelectEvent);
  const shownLineRef = useRef<Position[] | null>(null);
  const [ready, setReady] = useState(false);
  // 지도를 만들 때의 여백을 고르는 데 쓴다 (지도 만드는 effect는 한 번만 돈다)
  const edgeRef = useRef(edgeLayout);

  // 마커는 만들 때의 함수를 붙잡고 있으므로, 늘 최신 함수를 부르도록 ref에 둔다
  useEffect(() => {
    onSelectRef.current = onSelectEvent;
  });

  // 지도는 한 번만 만든다. maplibre-gl은 브라우저에서만 돌아가므로 여기서 불러온다
  useEffect(() => {
    let map: MapLibreMap | null = null;
    let cancelled = false;
    import("maplibre-gl").then(({ Map, Marker, setWorkerUrl }) => {
      if (cancelled || !containerRef.current) {
        return;
      }
      markerClassRef.current = Marker;
      // 번들러가 옮기지 않은 워커 파일을 public/에서 읽게 한다 (scripts/copy-maplibre-worker.mjs)
      setWorkerUrl("/maplibre-gl-worker.mjs");
      map = new Map({
        container: containerRef.current,
        style: MAP_STYLE,
        bounds: KOREA_BOUNDS,
        fitBoundsOptions: { padding: edgeRef.current ? EDGE_PADDING : PADDING },
        // 최소 줌과 끌 수 있는 범위는 지도 칸 크기에 맞춰 applyViewLimit이 정한다
        maxZoom: 9,
        dragRotate: false,
        pitchWithRotate: false,
        touchPitch: false,
        renderWorldCopies: false,
        // 출처는 아래 카드에 적는다 (지도 위 출처 칸이 범례를 가린다)
        attributionControl: false,
      });
      map.touchZoomRotate.disableRotation();
      map.keyboard.disableRotation();
      applyViewLimit(map);
      // 창 크기나 폰 방향이 바뀌면 지도 칸 크기도 바뀐다
      map.on("resize", () => applyViewLimit(map!));
      map.on("load", () => {
        mapRef.current = map;
        setReady(true);
      });
    });
    return () => {
      cancelled = true;
      map?.remove();
      mapRef.current = null;
    };
  }, []);

  // 스냅샷이 바뀌면 지금 보이는 전선에서 새 전선으로 옮긴다
  useEffect(() => {
    const map = mapRef.current;
    if (!ready || !map) {
      return;
    }
    const target = snapshot.frontLine.coordinates;
    const from = shownLineRef.current;
    // "개전 전" 칸과 개전 칸은 같은 선(38선)이라 움직이지 않는다
    const finish = () => {
      drawLine(map, target);
      shownLineRef.current = target;
      void source(map, "extra").setData(extraData(snapshot));
    };

    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (!from || from === target || reduceMotion) {
      finish();
      return;
    }

    // 추가 영역은 이동하는 동안 숨겼다가 도착하면 보인다
    void source(map, "extra").setData(EMPTY_DATA);
    let frame = 0;
    const start = performance.now();
    const step = (now: number) => {
      const t = Math.min(1, (now - start) / MOVE_MS);
      if (t === 1) {
        finish();
        return;
      }
      const line = interpolateLine(from, target, easeInOutCubic(t));
      drawLine(map, line);
      shownLineRef.current = line;
      frame = requestAnimationFrame(step);
    };
    frame = requestAnimationFrame(step);
    return () => cancelAnimationFrame(frame);
  }, [snapshot, ready]);

  // "개전 전" 칸에 들어가거나 나올 때 한반도를 가장자리 여백에 맞춰 다시 맞춘다
  useEffect(() => {
    const map = mapRef.current;
    if (!ready || !map || edgeRef.current === edgeLayout) {
      return;
    }
    edgeRef.current = edgeLayout;
    map.fitBounds(KOREA_BOUNDS, { padding: edgeLayout ? EDGE_PADDING : PADDING, duration: 600 });
  }, [edgeLayout, ready]);

  // 이 시기의 사건 마커. 사건이나 선택이 바뀌면 다시 그린다. 좌표가 없는 사건은 EdgeMarkers가 그린다
  useEffect(() => {
    const map = mapRef.current;
    const Marker = markerClassRef.current;
    if (!ready || !map || !Marker) {
      return;
    }
    const markers = events.flatMap((event) => {
      if (event.lon === null || event.lat === null) {
        return [];
      }
      const selected = event.id === selectedId;
      const element = markerElement(event, selected, (id) => onSelectRef.current(id));
      // 왼쪽 끝(원의 왼쪽 가장자리)을 기준으로 놓고, 원 반지름만큼 당겨 원 가운데가 사건 위치에 오게 한다
      return [
        new Marker({ element, anchor: "left", offset: [-13, 0] }).setLngLat([event.lon, event.lat]).addTo(map),
      ];
    });
    return () => markers.forEach((marker) => marker.remove());
  }, [events, selectedId, ready]);

  // maplibre-gl CSS가 지도 칸을 position: relative로 바꾸므로, 자리 잡기는 바깥 칸이 맡는다
  return (
    <div className="absolute inset-0">
      <div ref={containerRef} className="h-full w-full" role="img" aria-label="전선과 양측 지역 지도" />
      {edgeLayout && <EdgeMarkers events={events} selectedId={selectedId} onSelect={onSelectEvent} />}
    </div>
  );
}

// 지도 가장자리: 소련은 위, 중국은 왼쪽, 미국은 오른쪽 (CLAUDE.md 4장 "개전 전")
const REGION_NAME: Record<Region, string> = { north: "소련", west: "중국", east: "미국" };

// 양옆 띠의 높이 어림 (px). 이름 칸은 88px 폭에 한 줄 7자쯤 들어가고 세 줄까지 쓴다
const NAME_TAG_PX = 22; // 쪽 이름표
const BADGE_PX = 40; // 위 간격 + 아이콘 원 + 이름과의 간격
const LINE_PX = 15;

function columnHeight(list: WarEvent[], withLabels: boolean): number {
  return list.reduce(
    (sum, event) => sum + BADGE_PX + (withLabels ? Math.min(3, Math.ceil(event.title.length / 7)) * LINE_PX + 4 : 0),
    NAME_TAG_PX,
  );
}

// 지도 밖(모스크바·베이징·워싱턴) 사건. 가짜 좌표를 만들지 않고 지도 칸 가장자리에 줄지어 놓는다.
// 지도를 끌어도 움직이지 않는다. 띠의 크기는 EDGE_PADDING 안쪽이다 (위 52px, 양옆 88px).
// 양옆 띠는 위쪽 띠와 범례 사이에만 둔다. 사건 카드를 열어 지도 칸이 낮아지면 이름을 빼고 아이콘만,
// 그래도 모자라면 양옆 띠를 숨긴다 (카드를 닫으면 다시 보인다)
function EdgeMarkers({
  events,
  selectedId,
  onSelect,
}: {
  events: WarEvent[];
  selectedId: string | null;
  onSelect: (id: string) => void;
}) {
  const bandRef = useRef<HTMLDivElement>(null);
  const [bandHeight, setBandHeight] = useState(0);
  useEffect(() => {
    const band = bandRef.current;
    if (!band) {
      return;
    }
    const observer = new ResizeObserver(([entry]) => setBandHeight(entry.contentRect.height));
    observer.observe(band);
    return () => observer.disconnect();
  }, []);

  const inRegion = (region: Region) => events.filter((event) => event.region === region);
  const north = inRegion("north");
  const west = inRegion("west");
  const east = inRegion("east");
  const tallest = (withLabels: boolean) => Math.max(columnHeight(west, withLabels), columnHeight(east, withLabels));
  const sides = bandHeight >= tallest(true) ? "full" : bandHeight >= tallest(false) ? "icons" : "hidden";
  return (
    <>
      {north.length > 0 && (
        <div className="absolute inset-x-2 top-2 flex h-11 items-center justify-center gap-2">
          <RegionName region="north" />
          {north.map((event) => (
            <EdgeMarker key={event.id} event={event} selected={event.id === selectedId} onSelect={onSelect} row />
          ))}
        </div>
      )}
      <div ref={bandRef} className="pointer-events-none absolute inset-x-1 top-14 bottom-11">
        {sides !== "hidden" &&
          (["west", "east"] as const).map((region) => {
            const list = region === "west" ? west : east;
            return (
              list.length > 0 && (
                <div
                  key={region}
                  className={`pointer-events-auto absolute top-1/2 flex w-[88px] -translate-y-1/2 flex-col items-center ${
                    sides === "full" ? "gap-2.5" : "gap-1.5"
                  } ${region === "west" ? "left-0" : "right-0"}`}
                >
                  <RegionName region={region} />
                  {list.map((event) => (
                    <EdgeMarker
                      key={event.id}
                      event={event}
                      selected={event.id === selectedId}
                      onSelect={onSelect}
                      showLabel={sides === "full"}
                    />
                  ))}
                </div>
              )
            );
          })}
      </div>
    </>
  );
}

function RegionName({ region }: { region: Region }) {
  return (
    <span className="shrink-0 rounded-full border border-ink/40 bg-white/70 px-2 py-0.5 text-[11px] font-semibold text-muted">
      {REGION_NAME[region]}
    </span>
  );
}

// 지도 마커(markerElement)와 같은 모양: 흰 바탕 검정 선의 원, 고르면 색을 뒤집는다.
// 위쪽 띠는 아이콘 오른쪽에 두 줄까지, 양옆 띠는 좁아서 아이콘 아래에 세 줄까지 이름을 쓴다
function EdgeMarker({
  event,
  selected,
  onSelect,
  row = false,
  showLabel = true,
}: {
  event: WarEvent;
  selected: boolean;
  onSelect: (id: string) => void;
  row?: boolean;
  showLabel?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={() => onSelect(event.id)}
      aria-label={`${event.title}(${KIND_LABEL[event.kind]}) 사건 보기`}
      aria-pressed={selected}
      className={`flex min-w-0 items-center gap-1 ${row ? "shrink" : "flex-col"}`}
    >
      <span
        className={`flex size-[26px] shrink-0 items-center justify-center rounded-full border-2 border-ink ${
          selected ? "bg-ink text-white" : "bg-white text-ink"
        }`}
      >
        <KindIcon kind={event.kind} />
      </span>
      {showLabel && (
        <span
          className={`rounded-md bg-white/90 px-1.5 py-0.5 text-xs font-semibold text-ink ${
            row ? "line-clamp-2 text-left leading-tight" : "line-clamp-3 text-center leading-tight"
          }`}
        >
          {event.title}
        </span>
      )}
    </button>
  );
}
