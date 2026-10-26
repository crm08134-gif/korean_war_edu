-- 사건 카드를 새 형식으로 바꾼다 (CLAUDE.md 4장, docs/adr/0001-event-card-format.md)
-- 사건과 출처는 앱이 켜질 때 EventLoader가 data/events/에서 다시 넣으므로, 있던 행은 지우고 시작한다
DROP TABLE claim_evidence;
DROP TABLE event_claim;
DELETE FROM event;
DELETE FROM source_doc;

-- 화면에서 쓰지 않는 신뢰도 등급. 열을 지우면 CHECK 제약도 함께 지워진다
ALTER TABLE event DROP COLUMN confidence_tier;

-- 사건 종류. 지도 마커 아이콘을 고른다
ALTER TABLE event RENAME COLUMN category TO kind;
ALTER TABLE event
    ADD CONSTRAINT event_kind_check
        CHECK (kind IN ('battle', 'offensive', 'capture', 'landing', 'withdrawal', 'talks'));

-- 훅 대신 요약 2~3문장
ALTER TABLE event RENAME COLUMN hook TO summary;

-- 본문은 소제목·문단·표 블록의 배열(JSON)이다. 블록 모양과 빈 본문은 적재기가 JSON을 읽을 때 검사한다
ALTER TABLE event DROP COLUMN body;
ALTER TABLE event ADD COLUMN body JSONB NOT NULL;

-- 출처를 낸 기관. 카드에 "미 육군 군사사 센터, 책 제목, 3장"처럼 적는다
ALTER TABLE source_doc ADD COLUMN publisher TEXT NOT NULL;

-- 사건마다 출처 목록. 넣은 순서(id)대로 카드 맨 아래에 적는다
CREATE TABLE event_source (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id  TEXT NOT NULL REFERENCES event (id) ON DELETE CASCADE,
    source_id TEXT NOT NULL REFERENCES source_doc (id),
    locator   TEXT NOT NULL,

    CONSTRAINT event_source_unique UNIQUE (event_id, source_id, locator)
);
