-- 사건 카드 맨 위의 웹툰 그림 (CLAUDE.md 4장 "웹툰"). 사용자가 직접 만든 그림이라 사진(photo)의 라이선스 목록과 따로 둔다
-- [{"file": "…", "alt": "…"}, …] 순서대로 보인다. 그림 파일은 data/toons/에 있고, 파일이 있는지는 적재기가 본다
ALTER TABLE event
    ADD COLUMN toons JSONB NOT NULL DEFAULT '[]';
