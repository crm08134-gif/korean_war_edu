-- 게시판 3단계: 글, 댓글, 공감
-- 신고(report)는 4단계에서 V13으로 따로 만든다
--
-- 공통 규칙
-- * 글·댓글은 실제로 지우지 않는다(DELETE 안 함). status를 'DELETED'로 바꾼다.
--   신고 검토·분쟁 때 원래 내용을 확인해야 하고, 댓글이 달린 글이 사라지면 댓글이 고아가 되기 때문이다.
--   그래서 외래 키는 ON DELETE 옵션 없이 둔다(기본값 NO ACTION). 누가 실수로 DELETE를 해도 DB가 막는다.
-- * 길이 제한은 서버(@Size)가 먼저 검사하지만 DB에도 CHECK로 한 번 더 건다.
--   서버 코드에 구멍이 생겨도 DB에 이상한 값이 들어가지 않게 하는 마지막 방어선이다(심층 방어).
-- * char_length는 글자 수다. length(바이트 아님)와 같지만 뜻이 분명해서 이걸 쓴다. 한글 1자 = 1.

CREATE TABLE post (
                    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  -- 어느 게시판인가. 자유게시판은 없다(2026-11-01 결정)
                    board       TEXT        NOT NULL,
  -- 사건 게시판이면 event.id, 인물 게시판이면 person.id (둘 다 TEXT 키)
  -- 외래 키를 걸지 않는 이유: 사건·인물 테이블은 적재기가 data/ 파일로 지웠다 다시 채운다.
  -- 외래 키가 있으면 다시 채울 때 글이 달린 행을 못 지워 적재가 실패한다.
  -- 대신 글을 쓸 때 서버가 그 사건·인물이 있는지 확인한다.
                    target_id   TEXT        NOT NULL,
  -- 작성자. 탈퇴해도 member 행은 남으므로(닉네임만 지움) 외래 키를 걸 수 있다
                    author_id   BIGINT      NOT NULL REFERENCES member (id),
                    title       TEXT        NOT NULL,
                    body        TEXT        NOT NULL,
  -- VISIBLE: 보임 / BLINDED: 신고 2명으로 가려짐(4단계) / DELETED: 작성자가 지움
  -- 대문자인 이유: Java enum 이름을 그대로 저장한다(@Enumerated(STRING), member.role과 같은 방식)
                    status      TEXT        NOT NULL DEFAULT 'VISIBLE',
                    created_at  TIMESTAMPTZ NOT NULL,
  -- 수정한 적이 없으면 NULL. 화면에 "수정됨" 표시에 쓴다
                    updated_at  TIMESTAMPTZ,
                    deleted_at  TIMESTAMPTZ,

                    CONSTRAINT post_board_check  CHECK (board IN ('EVENT', 'PERSON')),
                    CONSTRAINT post_status_check CHECK (status IN ('VISIBLE', 'BLINDED', 'DELETED')),
  -- 앞뒤 공백만 있는 제목도 막는다(btrim = 양쪽 공백 제거)
                    CONSTRAINT post_title_check  CHECK (char_length(btrim(title)) BETWEEN 1 AND 100),
                    CONSTRAINT post_body_check   CHECK (char_length(btrim(body)) BETWEEN 1 AND 5000),
  -- 지운 글에는 지운 시각이 반드시 있고, 안 지운 글에는 없다. 둘이 어긋나는 버그를 DB가 잡는다
                    CONSTRAINT post_deleted_check CHECK ((status = 'DELETED') = (deleted_at IS NOT NULL))
);

-- 목록 조회: WHERE board = ? AND target_id = ? AND id < :cursor ORDER BY id DESC LIMIT 20
-- 이 순서 그대로 인덱스를 만들면 DB가 정렬 없이 인덱스를 따라 20개만 읽고 멈춘다.
-- 커서(마지막 id) 방식을 쓰는 이유: OFFSET 1000은 앞의 1000개를 읽고 버리지만, id < 커서는 바로 찾아간다.
CREATE INDEX post_target_idx ON post (board, target_id, id DESC);
-- 드롭다운 "전체": WHERE board = ? ORDER BY id DESC
CREATE INDEX post_board_idx ON post (board, id DESC);
-- 작성 횟수 제한(1분 1개, 하루 20개): WHERE author_id = ? AND created_at > ?
CREATE INDEX post_author_idx ON post (author_id, created_at);

CREATE TABLE comment (
                       id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                       post_id     BIGINT      NOT NULL REFERENCES post (id),
                       author_id   BIGINT      NOT NULL REFERENCES member (id),
  -- 대댓글은 없다(parent_id 없음). 한 단계만
                       body        TEXT        NOT NULL,
                       status      TEXT        NOT NULL DEFAULT 'VISIBLE',
                       created_at  TIMESTAMPTZ NOT NULL,
                       updated_at  TIMESTAMPTZ,
                       deleted_at  TIMESTAMPTZ,

                       CONSTRAINT comment_status_check  CHECK (status IN ('VISIBLE', 'BLINDED', 'DELETED')),
                       CONSTRAINT comment_body_check    CHECK (char_length(btrim(body)) BETWEEN 1 AND 1000),
                       CONSTRAINT comment_deleted_check CHECK ((status = 'DELETED') = (deleted_at IS NOT NULL))
);

-- 글 본문 아래 댓글: WHERE post_id = ? ORDER BY id (오래된 것부터)
CREATE INDEX comment_post_idx ON comment (post_id, id);
-- 작성 횟수 제한(1분 5개)
CREATE INDEX comment_author_idx ON comment (author_id, created_at);

-- 공감(좋아요). 한 회원은 글 하나에 한 번만. 다시 누르면 행을 지운다(취소)
-- 공감은 기록을 남길 이유가 없어 실제로 DELETE한다(글·댓글과 다름)
--
-- reaction(target_type, target_id, ...) 한 테이블로 합치지 않고 둘로 나눈 이유:
-- 대상이 글인지 댓글인지 바뀌는 칸에는 외래 키를 걸 수 없다. 나누면 둘 다 외래 키로 "없는 글에 공감"을 막는다.
--
-- 공감 수를 post에 like_count로 저장하지 않는 이유:
-- 두 사람이 동시에 누르면 숫자가 어긋날 수 있고(갱신 손실), 공감 행과 숫자가 따로 놀 수 있다.
-- 목록 20개의 공감 수는 COUNT로 세도 기본 키 인덱스 덕에 빠르다. 느려지면 그때 숫자 칸을 더한다.
CREATE TABLE post_like (
                         post_id    BIGINT      NOT NULL REFERENCES post (id),
                         member_id  BIGINT      NOT NULL REFERENCES member (id),
                         created_at TIMESTAMPTZ NOT NULL,
  -- 기본 키가 (글, 회원)이라 같은 사람이 두 번 누르면 DB가 거부한다(중복 공감 방지).
  -- 서버가 "이미 눌렀나" 확인한 뒤 넣어도, 동시에 두 요청이 오면 둘 다 통과할 수 있다. 그때 이 제약이 막는다
                         PRIMARY KEY (post_id, member_id)
);

CREATE TABLE comment_like (
                            comment_id BIGINT      NOT NULL REFERENCES comment (id),
                            member_id  BIGINT      NOT NULL REFERENCES member (id),
                            created_at TIMESTAMPTZ NOT NULL,
                            PRIMARY KEY (comment_id, member_id)
);
