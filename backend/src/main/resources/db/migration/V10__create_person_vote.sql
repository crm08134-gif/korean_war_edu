-- 관련 인물의 호감도 표: 좋아요 +1, 싫어요 -1. 같은 인물이라도 사건마다 따로 센다
-- 사건과 인물은 앱이 켜질 때마다 지우고 다시 넣으므로 외래 키를 걸지 않는다 (걸면 재시작할 때마다 표가 지워진다)
CREATE TABLE person_vote (
                           event_id  TEXT        NOT NULL,
                           person_id TEXT        NOT NULL,
  -- 회원 기능 전에는 브라우저마다 만든 익명 ID. 회원 기능이 생기면 회원 ID로 바꾼다
                           voter_id  UUID        NOT NULL,
                           value     SMALLINT    NOT NULL,
                           voted_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

  -- 한 기기는 사건마다 한 인물에 한 표만
                           PRIMARY KEY (event_id, person_id, voter_id),
                           CONSTRAINT person_vote_value_check
                             CHECK (value IN (1, -1))
);
