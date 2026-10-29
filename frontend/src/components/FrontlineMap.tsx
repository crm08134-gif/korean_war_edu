"use client";

import type { GeoJSONSource, Map as MapLibreMap, Marker as MapLibreMarker } from "maplibre-gl";
import { useEffect, useRef, useState } from "react";
import { KIND_LABEL, kindIconSvg } from "@/lib/event-kinds";
import type { WarEvent } from "@/lib/events";
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
      // 왼쪽 끝(원의 왼쪽 가장자리)을 기준으로 놓고, 원 반지름만큼 당겨 원 가운데가 사건 위치에 오게 한다
      return new Marker({ element, anchor: "left", offset: [-13, 0] })
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
