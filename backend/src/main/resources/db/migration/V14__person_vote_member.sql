-- 인물 좋아요·싫어요를 회원 표로 바꾼다 (2026-11-04 결정: 로그인해야 누르고, 그동안의 익명 표는 지운다)
-- 칸 하나를 고치는 대신 테이블을 새로 만든다. 기본 키와 칸 타입(UUID → BIGINT)이 함께 바뀌어 이쪽이 단순하다
-- V10은 이미 적용됐으므로 고치지 않고 새 번호로 낸다
DROP TABLE person_vote;

CREATE TABLE person_vote (
                           event_id  TEXT        NOT NULL,
                           person_id TEXT        NOT NULL,
  -- 누른 회원. 탈퇴해도 member 행은 남으므로 표도 남는다 (누가 눌렀는지는 알 수 없게 된다)
                           member_id BIGINT      NOT NULL REFERENCES member (id),
                           value     SMALLINT    NOT NULL,
                           voted_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

  -- 한 회원은 사건마다 한 인물에 한 표만
                           PRIMARY KEY (event_id, person_id, member_id),
                           CONSTRAINT person_vote_value_check CHECK (value IN (1, -1))
);
