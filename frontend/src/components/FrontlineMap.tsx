"use client";

import type { GeoJSONSource, Map as MapLibreMap, Marker as MapLibreMarker } from "maplibre-gl";
import { useEffect, useRef, useState } from "react";
import type { WarEvent } from "@/lib/events";
import { easeInOutCubic, interpolateLine } from "@/lib/geometry";
import { EMPTY_DATA, KOREA_BOUNDS, MAP_STYLE, MAX_BOUNDS, extraData, warData } from "@/lib/map-style";
import type { Position, Snapshot } from "@/lib/snapshots";

// 전선이 다음 스냅샷으로 옮겨 가는 시간
const MOVE_MS = 1200;

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
  element.setAttribute("aria-label", `${event.title} 사건 보기`);
  element.setAttribute("aria-pressed", String(selected));
  element.className = "flex items-center gap-1";

  // 흰 바탕에 검정 테두리: 북 빨강·남 파랑 어느 쪽 위에서도 보이고 두 색과 겹치지 않는다
  const dot = document.createElement("span");
  dot.className = selected
    ? "size-3.5 rounded-full border-[3px] border-ink bg-white"
    : "size-3 rounded-full border-2 border-ink bg-white";
  const label = document.createElement("span");
  label.className = "rounded-md bg-white/90 px-1.5 py-0.5 text-xs font-semibold whitespace-nowrap text-ink";
  label.textContent = event.title;
  element.append(dot, label);

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
}: {
  snapshot: Snapshot;
  events: WarEvent[];
  selectedId: string | null;
  onSelectEvent: (id: string) => void;
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<MapLibreMap | null>(null);
  const markerClassRef = useRef<typeof MapLibreMarker | null>(null);
  const onSelectRef = useRef(onSelectEvent);
  const shownLineRef = useRef<Position[] | null>(null);
  const [ready, setReady] = useState(false);

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
        // 아래쪽은 범례 자리만큼 비운다
        fitBoundsOptions: { padding: { top: 12, right: 12, bottom: 44, left: 12 } },
        maxBounds: MAX_BOUNDS,
        minZoom: 4,
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
    const finish = () => {
      drawLine(map, target);
      shownLineRef.current = target;
      void source(map, "extra").setData(extraData(snapshot));
    };

    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (!from || reduceMotion) {
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

  // 이 시기의 사건 마커. 사건이나 선택이 바뀌면 다시 그린다
  useEffect(() => {
    const map = mapRef.current;
    const Marker = markerClassRef.current;
    if (!ready || !map || !Marker) {
      return;
    }
    const markers = events.map((event) => {
      const selected = event.id === selectedId;
      const element = markerElement(event, selected, (id) => onSelectRef.current(id));
      // 왼쪽 끝(점의 왼쪽 가장자리)을 기준으로 놓고, 점 반지름만큼 당겨 점 가운데가 사건 위치에 오게 한다
      return new Marker({ element, anchor: "left", offset: [selected ? -7 : -6, 0] })
        .setLngLat([event.lon, event.lat])
        .addTo(map);
    });
    return () => markers.forEach((marker) => marker.remove());
  }, [events, selectedId, ready]);

  // maplibre-gl CSS가 지도 칸을 position: relative로 바꾸므로, 자리 잡기는 바깥 칸이 맡는다
  return (
    <div className="absolute inset-0">
      <div ref={containerRef} className="h-full w-full" role="img" aria-label="전선과 양측 지역 지도" />
    </div>
  );
}
