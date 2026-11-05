package io.github.crm08134gif.backend.chat;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

// AI에게 넘길 카드 내용 읽기와 하루 질문 횟수 세기
@Repository
class ChatRepository {

    // 사건 카드 하나를 글자 한 덩어리로 펼친다: 제목·날짜·장소·요약, 본문, 관련 인물, 출처.
    // 본문(body)은 JSON 배열이라 jsonb_array_elements로 블록을 하나씩 꺼내 종류별로 글자로 바꾼다.
    // 사진 블록은 CASE에 없어서 NULL이 되고, string_agg는 NULL을 건너뛴다. chr(10)은 줄바꿈
    private static final String EVENT_CONTEXT = """
            SELECT '제목: ' || e.title || chr(10)
                || '날짜: ' || e.event_date || COALESCE(' ~ ' || e.end_date, '') || chr(10)
                || '장소: ' || e.place_name || chr(10)
                || '요약: ' || e.summary || chr(10) || chr(10)
                || COALESCE((SELECT string_agg(
                                 CASE b.block->>'type'
                                     WHEN 'heading' THEN '## ' || (b.block->>'text')
                                     WHEN 'paragraph' THEN b.block->>'text'
                                     WHEN 'table' THEN '[표] ' || COALESCE(b.block->>'caption', '') || chr(10)
                                                       || (b.block->'columns')::text || chr(10)
                                                       || (b.block->'rows')::text
                                                       || COALESCE(chr(10) || (b.block->>'note'), '')
                                 END, chr(10) || chr(10) ORDER BY b.ord)
                             FROM jsonb_array_elements(e.body) WITH ORDINALITY AS b(block, ord)), '')
                || chr(10) || chr(10) || '관련 인물:' || chr(10)
                || COALESCE((SELECT string_agg('- ' || p.name || ' (' || ep.role || '): ' || ep.description,
                                               chr(10) ORDER BY ep.position)
                             FROM event_person ep
                             JOIN person p ON p.id = ep.person_id
                             WHERE ep.event_id = e.id AND (ep.status = 'reviewed' OR :includeDrafts)), '없음')
                || chr(10) || chr(10) || '출처:' || chr(10)
                || COALESCE((SELECT string_agg('- ' || s.publisher || ', ' || s.title || ', ' || es.locator,
                                               chr(10) ORDER BY es.id)
                             FROM event_source es
                             JOIN source_doc s ON s.id = es.source_id
                             WHERE es.event_id = e.id), '')
            FROM event e
            WHERE e.id = :id AND (e.status = 'reviewed' OR :includeDrafts)
            """;

    // 이야기 카드도 같은 방식. 이야기에는 날짜·장소·인물이 없다
    private static final String STORY_CONTEXT = """
            SELECT '제목: ' || st.title || chr(10)
                || '요약: ' || st.summary || chr(10) || chr(10)
                || COALESCE((SELECT string_agg(
                                 CASE b.block->>'type'
                                     WHEN 'heading' THEN '## ' || (b.block->>'text')
                                     WHEN 'paragraph' THEN b.block->>'text'
                                     WHEN 'table' THEN '[표] ' || COALESCE(b.block->>'caption', '') || chr(10)
                                                       || (b.block->'columns')::text || chr(10)
                                                       || (b.block->'rows')::text
                                                       || COALESCE(chr(10) || (b.block->>'note'), '')
                                 END, chr(10) || chr(10) ORDER BY b.ord)
                             FROM jsonb_array_elements(st.body) WITH ORDINALITY AS b(block, ord)), '')
                || chr(10) || chr(10) || '출처:' || chr(10)
                || COALESCE((SELECT string_agg('- ' || s.publisher || ', ' || s.title || ', ' || ss.locator,
                                               chr(10) ORDER BY ss.id)
                             FROM story_source ss
                             JOIN source_doc s ON s.id = ss.source_id
                             WHERE ss.story_id = st.id), '')
            FROM story st
            WHERE st.id = :id AND (st.status = 'reviewed' OR :includeDrafts)
            """;

    // 오늘 한 번 더 쓴다. 오늘 첫 질문이면 줄을 만들고(1), 있으면 used를 1 올린다.
    // 단 이미 한도(limit)에 닿았으면 올리지 않고 아무 줄도 돌려주지 않는다.
    // "세기"와 "올리기"가 한 문장이라 두 요청이 동시에 와도 한도를 넘지 않는다.
    // 날짜는 한국 시간으로 정한다 (DB 시간대가 UTC여도 한국 자정에 새로 센다)
    private static final String USE = """
            INSERT INTO chat_usage (member_id, day, used)
            VALUES (:memberId, (now() AT TIME ZONE 'Asia/Seoul')::date, 1)
            ON CONFLICT (member_id, day) DO UPDATE SET used = chat_usage.used + 1
            WHERE chat_usage.used < :limit
            RETURNING used
            """;

    // 구글에서 답을 못 받았으면 쓴 횟수를 돌려준다
    private static final String REFUND = """
            UPDATE chat_usage SET used = used - 1
            WHERE member_id = :memberId AND day = (now() AT TIME ZONE 'Asia/Seoul')::date AND used > 0
            """;

    // 모든 회원을 합친 오늘 횟수 (무료 한도 보호)
    private static final String TODAY_TOTAL = """
            SELECT COALESCE(sum(used), 0) FROM chat_usage
            WHERE day = (now() AT TIME ZONE 'Asia/Seoul')::date
            """;

    private final JdbcClient jdbc;

    ChatRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // 없는 사건이거나 검수 전(draft)이면 null
    String eventContext(String id, boolean includeDrafts) {
        return jdbc.sql(EVENT_CONTEXT)
                .param("id", id)
                .param("includeDrafts", includeDrafts)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    String storyContext(String id, boolean includeDrafts) {
        return jdbc.sql(STORY_CONTEXT)
                .param("id", id)
                .param("includeDrafts", includeDrafts)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    // 쓰고 난 오늘 횟수. 한도에 닿아 못 썼으면 null
    Integer use(long memberId, int limit) {
        return jdbc.sql(USE)
                .param("memberId", memberId)
                .param("limit", limit)
                .query(Integer.class)
                .optional()
                .orElse(null);
    }

    void refund(long memberId) {
        jdbc.sql(REFUND)
                .param("memberId", memberId)
                .update();
    }

    long todayTotal() {
        return jdbc.sql(TODAY_TOTAL)
                .query(Long.class)
                .single();
    }
}
