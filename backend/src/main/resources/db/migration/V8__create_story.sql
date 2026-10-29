-- 전쟁 속 이야기 (CLAUDE.md 5장): 전선·시기와 상관없이 언제든 목록에서 골라 읽는 글
-- 사건 카드와 같은 형식(요약, 본문 블록, 출처 목록, 사진)이지만 날짜·좌표·종류가 없다
-- 출처(source_doc)와 사진(photo)은 사건과 함께 쓴다. 앱이 켜질 때 StoryLoader가 data/stories/에서 다시 넣는다
CREATE TABLE story (
    id            TEXT PRIMARY KEY,
    title         TEXT NOT NULL,
    summary       TEXT NOT NULL,
    -- 대표 사진 (목록과 카드 위쪽). 없을 수도 있지만, 있으면 설명도 있어야 한다
    photo_file    TEXT REFERENCES photo (file),
    photo_caption TEXT,
    -- 사건과 같은 소제목·문단·표·사진 블록의 배열. 블록 모양은 적재기가 검사한다
    body          JSONB NOT NULL,
    status        TEXT NOT NULL,

    CONSTRAINT story_photo_caption_check
        CHECK ((photo_file IS NULL) = (photo_caption IS NULL)),
    CONSTRAINT story_status_check
        CHECK (status IN ('draft', 'reviewed'))
);

-- 이야기마다 출처 목록. 넣은 순서(id)대로 카드 맨 아래에 적는다
CREATE TABLE story_source (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    story_id  TEXT NOT NULL REFERENCES story (id) ON DELETE CASCADE,
    source_id TEXT NOT NULL REFERENCES source_doc (id),
    locator   TEXT NOT NULL,

    CONSTRAINT story_source_unique UNIQUE (story_id, source_id, locator)
);

-- 이야기가 쓰는 사진 (대표 사진 + 본문 사진 블록). event_photo와 같은 이유로 둔다
CREATE TABLE story_photo (
    story_id TEXT NOT NULL REFERENCES story (id) ON DELETE CASCADE,
    file     TEXT NOT NULL REFERENCES photo (file),

    PRIMARY KEY (story_id, file)
);
