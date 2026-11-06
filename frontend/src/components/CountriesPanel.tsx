"use client";

import { useEffect, useRef, useState } from "react";
import { CloseIcon } from "@/components/EventCard";
import { COUNTRY_SOURCES, fetchCountries, formatCount, type Country } from "@/lib/countries";

type Load = { status: "loading" } | { status: "error" } | { status: "ready"; countries: Country[] };

// 참전국 화면: 머리말의 "참전국" 버튼으로 여는 화면 전체 격자. 스냅샷과 상관없다.
// 전투병 파견국은 국기 + 참전 인원·전사자·사상자, 의료지원국은 국기 + 지원 부대·인원(모르면 비움).
// 물자지원국·의사표명국은 맨 아래 접는 칸에 작은 국기와 이름만
export default function CountriesPanel({ onClose }: { onClose: () => void }) {
  const [load, setLoad] = useState<Load>({ status: "loading" });
  const titleRef = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    titleRef.current?.focus();
    const controller = new AbortController();
    fetchCountries(controller.signal)
      .then((countries) => setLoad({ status: "ready", countries }))
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          console.error(error);
          setLoad({ status: "error" });
        }
      });
    return () => controller.abort();
  }, []);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  const countries = load.status === "ready" ? load.countries : [];
  const combat = countries.filter((country) => country.group === "combat");
  const medical = countries.filter((country) => country.group === "medical");
  const material = countries.filter((country) => country.group === "material");
  const intent = countries.filter((country) => country.group === "intent");

  return (
    <div role="dialog" aria-modal="true" aria-label="참전국" className="absolute inset-0 z-20 flex flex-col bg-white">
      <div className="flex items-center justify-between gap-3 px-5 pt-5 pb-3">
        <h2 ref={titleRef} tabIndex={-1} className="text-[22px] font-extrabold tracking-tight outline-none">
          참전국
        </h2>
        <button
          type="button"
          onClick={onClose}
          aria-label="참전국 닫기"
          className="-mr-2.5 flex size-11 shrink-0 items-center justify-center rounded-full text-muted"
        >
          <CloseIcon />
        </button>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto px-5 pb-[calc(24px+env(safe-area-inset-bottom))]">
        {load.status === "loading" ? (
          <Notice>참전국을 불러오는 중…</Notice>
        ) : load.status === "error" ? (
          <Notice>참전국을 불러오지 못했습니다. 백엔드가 켜져 있는지 확인해 주세요.</Notice>
        ) : countries.length === 0 ? (
          <Notice>아직 실린 나라가 없습니다.</Notice>
        ) : (
          <div className="flex flex-col gap-7">
            {combat.length > 0 && (
              <section className="flex flex-col gap-3">
                <SectionTitle title="전투병을 보낸 나라" count={combat.length} />
                <ul className="grid grid-cols-2 gap-2.5">
                  {combat.map((country) => (
                    <li key={country.id}>
                      <CombatTile country={country} />
                    </li>
                  ))}
                </ul>
              </section>
            )}
            {medical.length > 0 && (
              <section className="flex flex-col gap-3">
                <SectionTitle title="의료지원국" count={medical.length} />
                <ul className="grid grid-cols-2 gap-2.5">
                  {medical.map((country) => (
                    <li key={country.id}>
                      <MedicalTile country={country} />
                    </li>
                  ))}
                </ul>
              </section>
            )}
            {material.length + intent.length > 0 && <SupportList material={material} intent={intent} />}
            <footer className="flex flex-col gap-1.5 border-t border-fill pt-4 text-[11px] leading-relaxed text-muted">
              <p>참전 인원은 전쟁 동안 교대해 다녀간 사람까지 모두 센 연인원입니다.</p>
              <p>사상자는 전사·부상·실종·포로를 합친 수입니다. 숫자는 국가보훈부 표를 그대로 옮겼습니다.</p>
              <p className="mt-1 font-bold text-ink">출처</p>
              <ul className="flex flex-col gap-0.5">
                {COUNTRY_SOURCES.map((source) => (
                  <li key={source}>{source}</li>
                ))}
              </ul>
            </footer>
          </div>
        )}
      </div>
    </div>
  );
}

// 물자를 보낸 나라는 숫자 없이 국기와 이름만. 처음에는 접어 둔다 (나라가 많아 화면이 길어지므로)
function SupportList({ material, intent }: { material: Country[]; intent: Country[] }) {
  const [open, setOpen] = useState(false);
  return (
    <section className="flex flex-col gap-3">
      <button
        type="button"
        onClick={() => setOpen((now) => !now)}
        aria-expanded={open}
        aria-controls="support-countries"
        className="flex h-12 items-center justify-between rounded-2xl bg-fill px-4 text-left"
      >
        <span className="text-[15px] font-extrabold tracking-tight">
          물자를 보낸 나라 <span className="text-muted tabular-nums">{material.length + intent.length}</span>
        </span>
        <svg
          width="14"
          height="14"
          viewBox="0 0 14 14"
          aria-hidden="true"
          className={open ? "rotate-180 transition-transform" : "transition-transform"}
        >
          <path d="M3 5.5 7 9.5l4-4" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </button>
      {open && (
        <div id="support-countries" className="flex flex-col gap-4">
          {material.length > 0 && <SmallFlags title="물자지원국" countries={material} />}
          {intent.length > 0 && <SmallFlags title="물자지원 의사표명국" countries={intent} />}
        </div>
      )}
    </section>
  );
}

function SmallFlags({ title, countries }: { title: string; countries: Country[] }) {
  return (
    <div className="flex flex-col gap-2">
      <h4 className="text-[13px] font-bold text-muted">
        {title} <span className="tabular-nums">{countries.length}</span>
      </h4>
      <ul className="grid grid-cols-3 gap-x-2 gap-y-2.5">
        {countries.map((country) => (
          <li key={country.id} className="flex min-w-0 items-center gap-1.5">
            <Flag country={country} small />
            <span className="min-w-0 text-[12px] leading-tight break-keep">{country.name}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function SectionTitle({ title, count }: { title: string; count: number }) {
  return (
    <h3 className="text-[17px] font-extrabold tracking-tight">
      {title} <span className="text-muted tabular-nums">{count}</span>
    </h3>
  );
}

function CombatTile({ country }: { country: Country }) {
  return (
    <div className="flex h-full flex-col gap-2.5 rounded-2xl bg-fill p-3">
      <FlagName country={country} />
      <dl className="flex flex-col gap-1 text-[13px]">
        <Stat label="참전 인원" value={country.personnel} />
        <Stat label="전사" value={country.killed} />
        <Stat label="사상자" value={country.casualties} />
      </dl>
    </div>
  );
}

function MedicalTile({ country }: { country: Country }) {
  return (
    <div className="flex h-full flex-col gap-2.5 rounded-2xl bg-fill p-3">
      <FlagName country={country} />
      {/* 규모를 모르면 국기와 이름만 둔다 */}
      {(country.unit || country.personnel !== null) && (
        <p className="text-[13px] leading-snug">
          {country.unit}
          {country.unit && country.personnel !== null && " · "}
          {country.personnel !== null && <span className="tabular-nums">{formatCount(country.personnel)}명</span>}
        </p>
      )}
      {country.note && <p className="text-[11px] leading-snug text-muted">{country.note}</p>}
    </div>
  );
}

function Stat({ label, value }: { label: string; value: number | null }) {
  return (
    <div className="flex items-baseline justify-between gap-2">
      <dt className="text-muted">{label}</dt>
      <dd className="font-bold tabular-nums">{value === null ? "–" : `${formatCount(value)}명`}</dd>
    </div>
  );
}

function FlagName({ country }: { country: Country }) {
  return (
    <div className="flex items-center gap-2">
      <Flag country={country} />
      <span className="min-w-0 text-[15px] leading-tight font-bold break-keep">{country.name}</span>
    </div>
  );
}

// 1950년대 국기. 허용 라이선스로 확인한 국기가 없으면 이름 첫 글자를 국기 자리에 둔다
function Flag({ country, small = false }: { country: Country; small?: boolean }) {
  const size = small ? "h-4 w-6 text-[10px]" : "h-6 w-9 text-xs";
  if (!country.flag) {
    return (
      <span
        aria-hidden="true"
        className={`flex shrink-0 items-center justify-center rounded-[3px] bg-white font-bold text-muted ring-1 ring-black/10 ${size}`}
      >
        {country.name.replace(/^\(.*?\)/, "").slice(0, 1)}
      </span>
    );
  }
  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={`/flags/${country.flag}.svg`}
      alt=""
      className={`shrink-0 rounded-[3px] object-cover ring-1 ring-black/10 ${size}`}
    />
  );
}

function Notice({ children }: { children: React.ReactNode }) {
  return <p className="py-10 text-center text-sm text-muted">{children}</p>;
}
