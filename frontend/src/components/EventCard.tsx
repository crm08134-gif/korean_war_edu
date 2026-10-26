"use client";

import { useEffect, useRef } from "react";
import KindIcon from "@/components/KindIcon";
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

// 하단 패널 자리에 들어가는 사건 카드. 닫으면 다시 타임라인 패널로 돌아간다
export default function EventCard({ event, onClose }: { event: WarEvent; onClose: () => void }) {
  const headingRef = useRef<HTMLHeadingElement>(null);

  // 칩이나 마커를 눌러 열면 읽기 위치(포커스)를 카드 제목으로 옮긴다
  useEffect(() => {
    headingRef.current?.focus();
  }, [event.id]);

  // 사진 아래 출처 표기에 쓸 메타데이터를 파일 이름으로 찾는다
  const photoMeta = new Map(event.photos.map((meta) => [meta.file, meta]));

  return (
    <article aria-labelledby="event-card-title" className="flex flex-col gap-3">
      <div className="flex items-start justify-between gap-3">
        <div className="flex min-w-0 flex-col gap-1">
          <p className="flex items-center gap-1 text-xs text-muted tabular-nums">
            <KindIcon kind={event.kind} size={14} />
            {KIND_LABEL[event.kind]} · {formatEventDate(event)} · {event.placeName}
          </p>
          <h2
            id="event-card-title"
            ref={headingRef}
            tabIndex={-1}
            className="font-serif text-xl font-semibold tracking-tight outline-none"
          >
            {event.title}
          </h2>
        </div>
        <button
          type="button"
          onClick={onClose}
          aria-label="사건 카드 닫기"
          className="-mt-1.5 -mr-2.5 flex size-11 shrink-0 items-center justify-center rounded-full text-muted"
        >
          <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true">
            <path d="M4 4l8 8M12 4l-8 8" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
          </svg>
        </button>
      </div>

      {event.photoFile && event.photoCaption && (
        <Photo file={event.photoFile} caption={event.photoCaption} meta={photoMeta.get(event.photoFile)} />
      )}

      <p className="text-[15px] leading-relaxed font-semibold">{event.summary}</p>

      {event.body.map((block, i) =>
        block.type === "photo" ? (
          <Photo key={i} file={block.file} caption={block.caption} meta={photoMeta.get(block.file)} />
        ) : (
          <BodyBlock key={i} block={block} />
        ),
      )}

      {/* 출처 목록. 주장마다 근거를 달지 않고 사건 전체의 출처를 한 줄씩 적는다 */}
      <footer className="mt-2 flex flex-col gap-1 border-t border-rule-soft pt-3 text-xs leading-relaxed text-muted">
        <h3 className="font-semibold">출처</h3>
        <ul className="flex flex-col gap-0.5">
          {event.sources.map((source, i) => (
            <li key={i}>
              <SourceLine source={source} />
            </li>
          ))}
        </ul>
      </footer>
    </article>
  );
}

function BodyBlock({ block }: { block: Exclude<Block, { type: "photo" }> }) {
  switch (block.type) {
    case "heading":
      return <h3 className="mt-2 text-base font-semibold">{block.text}</h3>;
    case "paragraph":
      return <p className="text-[15px] leading-relaxed">{block.text}</p>;
    case "table":
      return <BodyTable table={block} />;
  }
}

// 폰 폭을 넘는 표는 표만 옆으로 밀어 본다
function BodyTable({ table }: { table: TableBlock }) {
  return (
    <div className="flex flex-col gap-1.5">
      <div className="-mx-5 overflow-x-auto px-5">
        <table className="w-full border-collapse text-sm tabular-nums">
          {table.caption && <caption className="pb-1.5 text-left font-semibold">{table.caption}</caption>}
          <thead>
            <tr className="border-b border-ink">
              {table.columns.map((column, i) => (
                <th key={i} scope="col" className="px-2 py-1.5 text-left font-semibold whitespace-nowrap first:pl-0">
                  {column}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {table.rows.map((row, i) => (
              <tr key={i} className="border-b border-rule-soft">
                {row.map((cell, j) =>
                  j === 0 ? (
                    <th key={j} scope="row" className="py-1.5 pr-2 text-left font-medium">
                      {cell}
                    </th>
                  ) : (
                    <td key={j} className="px-2 py-1.5">
                      {cell}
                    </td>
                  ),
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {table.note && <p className="text-xs text-muted">{table.note}</p>}
    </div>
  );
}

// 사진과 그 바로 아래 출처 표기 (CLAUDE.md 0장). 예: "사진: 미 육군 통신대 / NARA · 제목 · 퍼블릭 도메인"
function Photo({ file, caption, meta }: { file: string; caption: string; meta: PhotoMeta | undefined }) {
  return (
    <figure className="flex flex-col gap-1.5">
      {/* 사진은 미리 줄여 두고(data/photos/README.md) Next 이미지 최적화(sharp)는 쓰지 않는다 */}
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img
        src={`/photos/${encodeURIComponent(file)}`}
        alt={caption}
        loading="lazy"
        className="w-full rounded-[10px] bg-sea"
      />
      <figcaption className="flex flex-col gap-0.5">
        <span className="text-sm">{caption}</span>
        {meta && <PhotoCredit meta={meta} />}
      </figcaption>
    </figure>
  );
}

function PhotoCredit({ meta }: { meta: PhotoMeta }) {
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
function SourceLine({ source }: { source: SourceRef }) {
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
