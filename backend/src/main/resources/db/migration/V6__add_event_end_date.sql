-- 며칠 이어진 사건의 마지막 날 (CLAUDE.md 4장). 하루에 끝난 사건은 비워 둔다
-- 사건이 어느 스냅샷에 보이는지는 지금처럼 시작일(event_date)로 정한다
ALTER TABLE event
    ADD COLUMN end_date DATE,
    -- 시작일보다 뒤이고 전쟁 기간 안이어야 한다. 기간은 날짜 단위로만 적는다
    ADD CONSTRAINT event_end_date_check
        CHECK (end_date IS NULL
               OR (end_date > event_date
                   AND end_date <= DATE '1953-07-27'
                   AND date_precision = 'day'));
