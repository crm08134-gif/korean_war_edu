-- 게시판 4단계: 신고
-- 서로 다른 회원 2명이 신고하면 서버가 대상을 BLINDED로 바꾸고, 관리자 큐에 오른다 (CLAUDE.md 7장)
CREATE TABLE report (
                      id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  -- 글인지 댓글인지. target_id는 post.id 또는 comment.id
  -- 대상이 둘 중 하나로 바뀌는 칸이라 외래 키를 걸 수 없다. 대신 서버가 대상을 잠그고(FOR UPDATE) 있는지 확인한 뒤 넣는다.
  -- 글·댓글은 실제로 지우지 않으므로(status만 바꾼다) 신고가 가리키는 행은 사라지지 않는다
                      target_type  TEXT        NOT NULL,
                      target_id    BIGINT      NOT NULL,
                      reporter_id  BIGINT      NOT NULL REFERENCES member (id),
  -- 운영 정책의 금지 항목과 같은 목록
                      reason       TEXT        NOT NULL,
  -- "기타"일 때 쓰는 이유. 관리자만 본다
                      detail       TEXT,
                      created_at   TIMESTAMPTZ NOT NULL,
  -- 관리자가 처리하면 채운다. 셋은 함께 비거나 함께 찬다
                      handled_at   TIMESTAMPTZ,
                      handled_by   BIGINT      REFERENCES member (id),
                      result       TEXT,

                      CONSTRAINT report_target_check CHECK (target_type IN ('POST', 'COMMENT')),
                      CONSTRAINT report_reason_check
                        CHECK (reason IN ('HATE', 'DEFAMATION', 'MOCKERY', 'PRIVACY', 'SPAM', 'OTHER')),
                      CONSTRAINT report_detail_check
                        CHECK (detail IS NULL OR char_length(btrim(detail)) BETWEEN 1 AND 500),
  -- 기타는 이유가 꼭 있어야 한다
                      CONSTRAINT report_other_check CHECK (reason <> 'OTHER' OR detail IS NOT NULL),
                      CONSTRAINT report_result_check CHECK (result IN ('VISIBLE', 'BLINDED')),
                      CONSTRAINT report_handled_check CHECK (
                        (handled_at IS NULL) = (handled_by IS NULL) AND (handled_at IS NULL) = (result IS NULL)),
  -- 한 사람은 한 대상을 한 번만 신고한다. 혼자 여러 번 눌러 2명 기준을 채울 수 없다.
  -- 관리자가 되살린 뒤에도 같은 사람은 다시 신고할 수 없다 (같은 사람이 되풀이해 가리는 것을 막는다)
                      CONSTRAINT report_once_uk UNIQUE (target_type, target_id, reporter_id)
);

-- 관리자 큐와 "이 대상의 처리 안 된 신고 수": 처리 안 된 것만 들어가는 작은 인덱스(부분 인덱스)
CREATE INDEX report_open_idx ON report (target_type, target_id) WHERE handled_at IS NULL;
-- 신고 횟수 제한(하루 20건)
CREATE INDEX report_reporter_idx ON report (reporter_id, created_at);
