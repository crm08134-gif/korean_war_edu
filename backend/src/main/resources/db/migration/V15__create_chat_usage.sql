-- AI 질문 횟수. 질문과 답은 저장하지 않고, 회원별·날짜별(한국 날짜) 횟수만 센다
CREATE TABLE chat_usage (
    member_id BIGINT NOT NULL REFERENCES member (id),
    day       DATE   NOT NULL,
    used      INT    NOT NULL,

    -- 회원 한 명은 하루에 한 줄. 두 번째 질문부터는 이 줄의 used를 올린다
    PRIMARY KEY (member_id, day),
    CONSTRAINT chat_usage_used_check CHECK (used >= 0)
);
