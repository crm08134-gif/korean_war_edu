-- 사건 카드의 관련 인물. 인물은 data/people/people.csv에, 사건마다 인물 설명은 사건 파일의 people에 있다
-- 앱이 켜질 때 EventLoader가 둘 다 다시 넣는다
CREATE TABLE person (
                      id            TEXT PRIMARY KEY,
                      name          TEXT NOT NULL,
                      name_original TEXT NOT NULL,
  -- 당시 국기. 그림은 frontend/public/flags/<country>.svg
                      country       TEXT NOT NULL,
                      side          TEXT NOT NULL,
  -- 허용 목록 안의 사진이 없으면 비워 둔다. 화면에 첫 글자 원이 나온다
                      photo_file    TEXT REFERENCES photo (file),

                      CONSTRAINT person_side_check
                        CHECK (side IN ('un', 'communist'))
);

-- 사건마다 인물 한 명: 그 사건 당시 직책과 그 사건에서 한 일
CREATE TABLE event_person (
                            event_id    TEXT NOT NULL REFERENCES event (id) ON DELETE CASCADE,
                            person_id   TEXT NOT NULL REFERENCES person (id),
  -- 사건 파일에 적은 순서
                            position    INT  NOT NULL,
                            role        TEXT NOT NULL,
                            description TEXT NOT NULL,
  -- 사건과 따로 검수한다. 사건이 reviewed여도 인물 설명은 draft일 수 있다
                            status      TEXT NOT NULL,

                            PRIMARY KEY (event_id, person_id),
                            CONSTRAINT event_person_status_check
                              CHECK (status IN ('draft', 'reviewed'))
);

-- 인물 설명마다 출처 목록
CREATE TABLE event_person_source (
                                   id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                   event_id  TEXT NOT NULL,
                                   person_id TEXT NOT NULL,
                                   source_id TEXT NOT NULL REFERENCES source_doc (id),
                                   locator   TEXT NOT NULL,

                                   CONSTRAINT event_person_source_person_fk
                                     FOREIGN KEY (event_id, person_id) REFERENCES event_person (event_id, person_id) ON DELETE CASCADE,
                                   CONSTRAINT event_person_source_unique
                                     UNIQUE (event_id, person_id, source_id, locator)
);
