"use client";

import { useEffect, useRef, useState } from "react";
import { CloseIcon, ContentCard } from "@/components/EventCard";
import { LICENSE_LABEL, type PhotoMeta } from "@/lib/events";
import { fetchStories, type Story } from "@/lib/stories";

type Load = { status: "loading" } | { status: "error" } | { status: "ready"; stories: Story[] };

// 전쟁 속 이야기: 헤더의 "이야기" 버튼으로 여는 화면 전체 목록. 시기와 상관없어 스냅샷을 넘겨도 그대로다.
// 목록에서 하나를 고르면 같은 자리에서 사건 카드와 같은 모양으로 읽는다
export default function StoriesPanel({ onClose }: { onClose: () => void }) {
  const [load, setLoad] = useState<Load>({ status: "loading" });
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const titleRef = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    const controller = new AbortController();
    fetchStories(controller.signal)
      .then((stories) => setLoad({ status: "ready", stories }))
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          console.error(error);
          setLoad({ status: "error" });
        }
      });
    return () => controller.abort();
  }, []);

  const stories = load.status === "ready" ? load.stories : [];
  const selected = stories.find((story) => story.id === selectedId) ?? null;

  // 열면 읽기 위치(포커스)를 화면 제목으로 옮긴다. 이야기를 닫고 목록으로 돌아올 때도 같다
  useEffect(() => {
    if (!selected) titleRef.current?.focus();
  }, [selected]);

  // Esc: 읽던 이야기가 있으면 목록으로, 목록이면 화면을 닫는다
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key !== "Escape") return;
      if (selectedId) setSelectedId(null);
      else onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [selectedId, onClose]);

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="전쟁 속 이야기"
      className="absolute inset-0 z-20 flex flex-col bg-white"
    >
      <div className="flex items-center justify-between gap-3 px-5 pt-5 pb-3">
        {selected ? (
          <button
            type="button"
            onClick={() => setSelectedId(null)}
            className="-ml-2 flex h-11 items-center gap-1 rounded-full px-2 text-sm font-medium"
          >
            <svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true">
              <path
                d="M7.5 2.5 4 6l3.5 3.5"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.6"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
            목록
          </button>
        ) : (
          <h2 ref={titleRef} tabIndex={-1} className="text-[22px] font-extrabold tracking-tight outline-none">
            전쟁 속 이야기
          </h2>
        )}
        <button
          type="button"
          onClick={onClose}
          aria-label="전쟁 속 이야기 닫기"
          className="-mr-2.5 flex size-11 shrink-0 items-center justify-center rounded-full text-muted"
        >
          <CloseIcon />
        </button>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto px-5 pt-4 pb-[calc(24px+env(safe-area-inset-bottom))]">
        {selected ? (
          <ContentCard content={selected} meta="전쟁 속 이야기" />
        ) : load.status === "loading" ? (
          <Notice>이야기를 불러오는 중…</Notice>
        ) : load.status === "error" ? (
          <Notice>이야기를 불러오지 못했습니다. 백엔드가 켜져 있는지 확인해 주세요.</Notice>
        ) : stories.length === 0 ? (
          <Notice>아직 실린 이야기가 없습니다.</Notice>
        ) : (
          <ul className="grid grid-cols-2 gap-x-3 gap-y-5 sm:grid-cols-3">
            {stories.map((story) => (
              <li key={story.id}>
                <StoryTile story={story} onSelect={() => setSelectedId(story.id)} />
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}

// 목록 한 칸: 대표 사진 썸네일, 그 바로 아래 출처(CLAUDE.md 0장), 제목과 요약 첫머리
function StoryTile({ story, onSelect }: { story: Story; onSelect: () => void }) {
  const meta = story.photos.find((photo) => photo.file === story.photoFile);
  return (
    <button type="button" onClick={onSelect} className="flex w-full flex-col gap-1 text-left">
      {story.photoFile ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={`/photos/${encodeURIComponent(story.photoFile)}`}
          alt=""
          loading="lazy"
          className="aspect-video w-full rounded-xl bg-sea object-cover"
        />
      ) : (
        <NoPhoto />
      )}
      {meta && <ThumbnailCredit meta={meta} />}
      <span className="mt-0.5 line-clamp-2 text-[15px] leading-snug font-bold">{story.title}</span>
      <span className="line-clamp-2 text-xs leading-snug text-muted">{story.summary}</span>
    </button>
  );
}

// 목록에서는 링크 없이 짧게 적는다(원본·라이선스 링크는 카드의 사진 아래에 있다).
// 썸네일은 16:9로 잘라 보이므로, 바꾼 것 표기에 자르기를 더한다
function ThumbnailCredit({ meta }: { meta: PhotoMeta }) {
  const license = LICENSE_LABEL[meta.license] ?? meta.license;
  const modified = [meta.modified, "목록에서 자름"].filter(Boolean).join(", ");
  return (
    <span className="line-clamp-2 text-[10px] leading-tight text-muted">
      사진: {meta.author} · {license} · {modified}
    </span>
  );
}

// 허용 목록 안에서 쓸 사진을 찾지 못한 이야기는 사진 대신 빈 칸을 둔다
function NoPhoto() {
  return (
    <span className="flex aspect-video w-full items-center justify-center rounded-xl bg-sea text-muted">
      <svg width="28" height="28" viewBox="0 0 28 28" aria-hidden="true">
        <path
          d="M6 5h11l5 5v13H6z M17 5v5h5 M10 14h8 M10 18h8"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.6"
          strokeLinejoin="round"
        />
      </svg>
    </span>
  );
}

function Notice({ children }: { children: React.ReactNode }) {
  return <p className="py-10 text-center text-sm text-muted">{children}</p>;
}
