-- 사건 카드의 사진과 그 이용 조건 (CLAUDE.md 0장 "사진·그림·지도 이미지")
-- 사진 파일과 photos.csv는 data/photos/에 있고, 앱이 켜질 때 EventLoader가 다시 넣는다
CREATE TABLE photo (
    file         TEXT PRIMARY KEY,
    title        TEXT NOT NULL,
    author       TEXT NOT NULL,
    source_url   TEXT NOT NULL,
    license      TEXT NOT NULL,
    license_url  TEXT,
    retrieved_on DATE NOT NULL,
    modified     TEXT,

    -- 허용 목록 밖의 라이선스는 넣을 수 없다
    CONSTRAINT photo_license_check
        CHECK (license IN ('public-domain', 'cc0',
                           'cc-by-2.0', 'cc-by-2.5', 'cc-by-3.0', 'cc-by-4.0',
                           'cc-by-sa-2.0', 'cc-by-sa-2.5', 'cc-by-sa-3.0', 'cc-by-sa-4.0',
                           'kogl-1')),
    -- 퍼블릭 도메인이 아니면 라이선스 주소도 적는다
    CONSTRAINT photo_license_url_check
        CHECK (license = 'public-domain' OR license_url IS NOT NULL)
);

-- 대표 사진 (카드 위쪽). 없을 수도 있지만, 있으면 설명도 있어야 한다
ALTER TABLE event
    ADD COLUMN photo_file TEXT REFERENCES photo (file),
    ADD COLUMN photo_caption TEXT,
    ADD CONSTRAINT event_photo_caption_check
        CHECK ((photo_file IS NULL) = (photo_caption IS NULL));

-- 사건이 쓰는 사진 (대표 사진 + 본문 사진 블록). 본문은 JSON이라 외래 키를 걸 수 없어서,
-- 적재기가 쓰인 사진마다 한 줄씩 넣어 photos.csv에 없는 사진과 한 카드에 두 번 쓴 사진을 막는다
CREATE TABLE event_photo (
    event_id TEXT NOT NULL REFERENCES event (id) ON DELETE CASCADE,
    file     TEXT NOT NULL REFERENCES photo (file),

    PRIMARY KEY (event_id, file)
);
