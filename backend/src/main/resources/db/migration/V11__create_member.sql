-- 회원. 소셜 로그인만 쓰므로 비밀번호·이메일은 저장하지 않는다
-- 이름을 user로 하지 않은 것은 PostgreSQL 예약어라서다
CREATE TABLE member (
                      id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  -- 로그인 제공자와 그쪽의 회원 번호. 탈퇴하면 둘 다 지운다
                      provider            TEXT,
                      provider_user_id    TEXT,
  -- 첫 로그인 직후에는 비어 있고, 닉네임 화면에서 정한다. 탈퇴하면 지운다
                      nickname            TEXT,
                      nickname_changed_at TIMESTAMPTZ,
                      role                TEXT        NOT NULL,
  -- 이 시각까지 글쓰기 금지 (관리자가 정한다)
                      write_banned_until  TIMESTAMPTZ,
                      created_at          TIMESTAMPTZ NOT NULL,
                      withdrawn_at        TIMESTAMPTZ,

                      CONSTRAINT member_provider_check CHECK (provider IN ('kakao', 'google')),
                      CONSTRAINT member_role_check CHECK (role IN ('USER', 'ADMIN')),
  -- 탈퇴하지 않은 회원은 반드시 로그인 정보가 있다
                      CONSTRAINT member_login_check CHECK (
                        withdrawn_at IS NOT NULL OR (provider IS NOT NULL AND provider_user_id IS NOT NULL))
);

-- 같은 카카오 계정으로 회원이 두 번 생기지 않게. 탈퇴 회원은 NULL이라 여럿이어도 된다
CREATE UNIQUE INDEX member_provider_user_uk ON member (provider, provider_user_id);
-- 닉네임 중복 금지. 영문 대소문자만 다른 것도 같은 닉네임으로 본다
CREATE UNIQUE INDEX member_nickname_uk ON member (lower(nickname));

-- 리프레시 토큰. 원문은 브라우저 쿠키에만 있고 DB에는 해시만 둔다
CREATE TABLE refresh_token (
                             id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                             member_id   BIGINT      NOT NULL REFERENCES member (id),
  -- 한 기기의 로그인 묶음. 갱신해도 같은 값이 이어진다. 기기별 로그아웃과 탈취 감지에 쓴다
                             family_id   UUID        NOT NULL,
                             token_hash  TEXT        NOT NULL UNIQUE,
  -- 기기 목록에 "Chrome · Windows"처럼 보여 주기 위한 값
                             user_agent  TEXT,
                             created_at  TIMESTAMPTZ NOT NULL,
                             expires_at  TIMESTAMPTZ NOT NULL,
  -- 폐기된 시각. 갱신·로그아웃·탈퇴 때 채운다
                             revoked_at  TIMESTAMPTZ
);

-- "이 회원의 토큰 모두 폐기", "이 기기 묶음 폐기"를 빠르게
CREATE INDEX refresh_token_member_idx ON refresh_token (member_id);
CREATE INDEX refresh_token_family_idx ON refresh_token (family_id);
