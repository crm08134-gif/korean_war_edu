-- 개전 전 사건 (1949~1950. 6.): 전쟁이 일어나기까지 소련·중국·미국의 움직임
-- 모스크바·베이징·워싱턴은 지도 밖이라 좌표 대신 지도 가장자리(region)에 놓는다

-- 날짜 범위의 시작을 1949-01-01로 넓힌다. 끝(정전협정 조인일)은 그대로
ALTER TABLE event
    DROP CONSTRAINT event_date_in_war_check,
    ADD CONSTRAINT event_date_range_check
        CHECK (event_date BETWEEN DATE '1949-01-01' AND DATE '1953-07-27');

-- 지도 가장자리: north 소련(위), east 미국(오른쪽), west 중국(왼쪽)
ALTER TABLE event
    ADD COLUMN region TEXT,
    ADD CONSTRAINT event_region_check
        CHECK (region IN ('north', 'east', 'west'));

-- 좌표와 가장자리 중 정확히 하나만 있어야 마커를 어디에 둘지 정해진다
ALTER TABLE event ALTER COLUMN geom DROP NOT NULL;
ALTER TABLE event
    ADD CONSTRAINT event_place_check
        CHECK (num_nonnulls(geom, region) = 1);

-- 새 종류: aid 원조·지원, policy 선언·정책
ALTER TABLE event
    DROP CONSTRAINT event_kind_check,
    ADD CONSTRAINT event_kind_check
        CHECK (kind IN ('battle', 'offensive', 'capture', 'landing', 'withdrawal', 'talks', 'aid', 'policy'));
