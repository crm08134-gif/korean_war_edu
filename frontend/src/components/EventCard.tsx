"use client";

import { useEffect, useId, useRef, useState } from "react";
import KindIcon from "@/components/KindIcon";
import PeopleSheet from "@/components/PeopleSheet";
import { KIND_LABEL } from "@/lib/event-kinds";
import {
  LICENSE_LABEL,
  formatEventDate,
  type Block,
  type PhotoMeta,
  type SourceRef,
  type TableBlock,
  type WarEvent,
} from "@/lib/events";
import type { Story } from "@/lib/stories";

// 사건 카드. 2~3분 읽을거리라 지도를 덮고 화면 전체로 연다. 닫으면 보던 지도로 돌아간다
export default function EventCard({ event, onClose }: { event: WarEvent; onClose: () => void }) {
  // 관련 인물 시트. 다른 사건을 열면 닫힌 채로 시작한다
  const [peopleOpenFor, setPeopleOpenFor] = useState<string | null>(null);
  const peopleOpen = peopleOpenFor === event.id;

  return (
    <div role="dialog" aria-modal="true" aria-label={event.title} className="absolute inset-0 z-10 flex flex-col bg-white">
      <div className="flex items-center justify-between px-3 py-2">
        <button
          type="button"
          onClick={onClose}
          aria-label="사건 카드 닫고 지도로"
          className="flex size-11 items-center justify-center rounded-full bg-fill"
        >
          <BackIcon />
        </button>
        {/* 인물이 없는 사건에는 버튼을 두지 않는다 */}
        {event.people.length > 0 && (
          <button
            type="button"
            onClick={() => setPeopleOpenFor(event.id)}
            className="flex h-11 items-center gap-1.5 rounded-full bg-fill px-4 text-sm font-bold"
          >
            <PeopleIcon />
            관련 인물 {event.people.length}
          </button>
        )}
      </div>
      {/* 다른 사건을 열면 맨 위부터 읽도록 스크롤 칸을 새로 만든다 */}
      <div key={event.id} className="min-h-0 flex-1 overflow-y-auto px-5 pb-[calc(24px+env(safe-area-inset-bottom))]">
        <ContentCard
          content={event}
          meta={
            <>
              <KindIcon kind={event.kind} size={14} />
              {KIND_LABEL[event.kind]} · {formatEventDate(event)} · {event.placeName}
            </>
          }
        />
      </div>
      {peopleOpen && <PeopleSheet eventId={event.id} title={event.title} people={event.people} onClose={() => setPeopleOpenFor(null)} />}
    </div>
  );
}

// 사건 카드와 전쟁 속 이야기 카드가 함께 쓰는 몸통: 제목, 대표 사진, 요약, 본문, 출처 목록.
// 닫기 버튼은 감싸는 화면(EventCard, StoriesPanel)의 머리말에 있다
export function ContentCard({ content, meta }: { content: WarEvent | Story; meta: React.ReactNode }) {
  const headingRef = useRef<HTMLHeadingElement>(null);
  // 사건 카드와 이야기 카드가 한 화면에 함께 있을 수 있어 제목 id를 겹치지 않게 만든다
  const headingId = useId();

  // 칩이나 마커를 눌러 열면 읽기 위치(포커스)를 카드 제목으로 옮긴다
  useEffect(() => {
    headingRef.current?.focus();
  }, [content.id]);

  // 사진 아래 출처 표기에 쓸 메타데이터를 파일 이름으로 찾는다
  const photoMeta = new Map(content.photos.map((photo) => [photo.file, photo]));

  return (
    <article aria-labelledby={headingId} className="flex flex-col gap-4">
      <div className="flex flex-col gap-1.5 pt-2">
        <p className="flex items-center gap-1 text-[13px] font-medium text-muted tabular-nums">{meta}</p>
        <h2
          id={headingId}
          ref={headingRef}
          tabIndex={-1}
          className="text-[28px] leading-tight font-extrabold tracking-tight outline-none"
        >
          {content.title}
        </h2>
      </div>

      {content.photoFile && content.photoCaption && (
        <Photo file={content.photoFile} caption={content.photoCaption} meta={photoMeta.get(content.photoFile)} />
      )}

      <p className="rounded-2xl bg-fill px-4 py-3.5 text-[15px] leading-[1.7] font-medium">{content.summary}</p>

      {content.body.map((block, i) =>
        block.type === "photo" ? (
          <Photo key={i} file={block.file} caption={block.caption} meta={photoMeta.get(block.file)} />
        ) : (
          <BodyBlock key={i} block={block} />
        ),
      )}

      {/* 출처 목록. 주장마다 근거를 달지 않고 글 전체의 출처를 한 줄씩 적는다 */}
      <footer className="mt-4 flex flex-col gap-1.5 border-t border-fill pt-4 text-xs leading-relaxed text-muted">
        <h3 className="font-bold">출처</h3>
        <ul className="flex flex-col gap-0.5">
          {content.sources.map((source, i) => (
            <li key={i}>
              <SourceLine source={source} />
            </li>
          ))}
        </ul>
      </footer>
    </article>
  );
}

function PeopleIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true">
      <circle cx="6" cy="5" r="2.5" fill="none" stroke="currentColor" strokeWidth="1.6" />
      <path d="M1.5 13.5c.6-2.6 2.3-4 4.5-4s3.9 1.4 4.5 4" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
      <circle cx="11.5" cy="5.5" r="2" fill="none" stroke="currentColor" strokeWidth="1.4" />
      <path d="M11.8 9.6c1.5.2 2.5 1.4 2.9 3.4" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
    </svg>
  );
}

function BackIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 12 12" aria-hidden="true">
      <path
        d="M7.5 2.5 4 6l3.5 3.5"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export function CloseIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true">
      <path d="M4 4l8 8M12 4l-8 8" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  );
}

function BodyBlock({ block }: { block: Exclude<Block, { type: "photo" }> }) {
  switch (block.type) {
    case "heading":
      return <h3 className="mt-3 text-xl font-extrabold tracking-tight">{block.text}</h3>;
    case "paragraph":
      return <p className="text-base leading-[1.8]">{block.text}</p>;
    case "table":
      return <BodyTable table={block} />;
  }
}

// 폰 폭에서는 여러 칸 표가 너무 좁아서, 행마다 흰 칸 하나로 쌓는다.
// 첫 열은 칸 제목, 나머지 열은 그 아래에 열 이름과 함께 놓는다 (값이 둘이면 나란히, 셋 이상이면 세로로).
// 화면 읽기 프로그램에는 그대로 표로 읽히도록 table 요소의 display만 바꾼다
function BodyTable({ table }: { table: TableBlock }) {
  const values = table.columns.length - 1;
  // 열이 둘(예: 날짜 · 일)이면 값 열 이름은 뻔해서 적지 않는다
  const showLabels = values >= 2;
  return (
    <div className="flex flex-col rounded-2xl bg-fill p-1.5">
      <table className="block text-sm tabular-nums">
        {table.caption && (
          <caption className="block px-2.5 pt-2 pb-2.5 text-left text-[15px] font-extrabold">{table.caption}</caption>
        )}
        <thead className="sr-only">
          <tr>
            {table.columns.map((column, i) => (
              <th key={i} scope="col">
                {column}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="flex flex-col gap-1.5">
          {table.rows.map((row, i) => (
            <tr
              key={i}
              className={`grid gap-x-3 gap-y-2 rounded-xl bg-white px-3 py-3 ${values === 2 ? "grid-cols-2" : "grid-cols-1"}`}
            >
              <th scope="row" className="col-span-full text-left text-[13px] font-bold">
                {row[0]}
              </th>
              {row.slice(1).map((cell, j) => (
                <td key={j} className="flex flex-col gap-0.5 leading-snug">
                  {showLabels && (
                    <span aria-hidden="true" className="text-[11px] font-medium text-muted">
                      {table.columns[j + 1]}
                    </span>
                  )}
                  <span className={cell === "—" ? "text-muted" : ""}>{cell}</span>
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      {table.note && <p className="px-2.5 pt-2 pb-1.5 text-xs text-muted">{table.note}</p>}
    </div>
  );
}

// 사진과 그 바로 아래 출처 표기 (CLAUDE.md 0장). 예: "사진: 미 육군 통신대 / NARA · 제목 · 퍼블릭 도메인"
function Photo({ file, caption, meta }: { file: string; caption: string; meta: PhotoMeta | undefined }) {
  return (
    <figure className="-mx-5 flex flex-col gap-1.5">
      {/* 사진은 미리 줄여 두고(data/photos/README.md) Next 이미지 최적화(sharp)는 쓰지 않는다 */}
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img
        src={`/photos/${encodeURIComponent(file)}`}
        alt={caption}
        loading="lazy"
        className="w-full bg-sea"
      />
      <figcaption className="flex flex-col gap-0.5 px-5">
        <span className="text-[13px] leading-relaxed">{caption}</span>
        {meta && <PhotoCredit meta={meta} />}
      </figcaption>
    </figure>
  );
}

export function PhotoCredit({ meta }: { meta: PhotoMeta }) {
  const license = LICENSE_LABEL[meta.license] ?? meta.license;
  return (
    <span className="text-xs text-muted">
      사진: {meta.author} ·{" "}
      <a href={meta.sourceUrl} target="_blank" rel="noreferrer" className="underline underline-offset-2">
        {meta.title}
      </a>{" "}
      ·{" "}
      {meta.licenseUrl ? (
        <a href={meta.licenseUrl} target="_blank" rel="noreferrer" className="underline underline-offset-2">
          {license}
        </a>
      ) : (
        license
      )}
      {meta.modified && <> · {meta.modified}</>}
    </span>
  );
}

// "미 육군 군사사 센터, South to the Naktong, North to the Yalu, 3장". 주소가 있으면 제목을 원본에 잇는다
export function SourceLine({ source }: { source: SourceRef }) {
  const title = source.url ? (
    <a href={source.url} target="_blank" rel="noreferrer" className="underline underline-offset-2">
      {source.title}
    </a>
  ) : (
    source.title
  );
  return (
    <>
      {source.publisher}, {title}, {source.locator}
    </>
  );
}
