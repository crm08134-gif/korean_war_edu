package io.github.crm08134gif.backend.vote;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class VoteRepository {

    // 그 사건의 인물마다 좋아요·싫어요 수와 내 표. 표가 없는 인물도 0으로 나오게 event_person에서 시작한다
    // 로그인하지 않았으면 :memberId가 null이라 비교가 모두 거짓이 되고 mine은 0이다
    // CAST: null만 넘어오면 DB가 타입을 짐작하지 못할 수 있어 BIGINT라고 알려 준다
    private static final String COUNTS = """
            SELECT ep.person_id,
                   COUNT(v.value) FILTER (WHERE v.value = 1)  AS up,
                   COUNT(v.value) FILTER (WHERE v.value = -1) AS down,
                   COALESCE(MAX(v.value) FILTER (WHERE v.member_id = CAST(:memberId AS BIGINT)), 0) AS mine
            FROM event_person ep
            LEFT JOIN person_vote v ON v.event_id = ep.event_id AND v.person_id = ep.person_id
            WHERE ep.event_id = :eventId
            GROUP BY ep.person_id
            """;

    // 그 사건에 실제로 있는 인물일 때만 넣는다. 이미 표가 있으면 값을 바꾼다
    private static final String SAVE = """
            INSERT INTO person_vote (event_id, person_id, member_id, value)
            SELECT ep.event_id, ep.person_id, :memberId, :value
            FROM event_person ep
            WHERE ep.event_id = :eventId AND ep.person_id = :personId
            ON CONFLICT (event_id, person_id, member_id)
                DO UPDATE SET value = EXCLUDED.value, voted_at = now()
            """;

    private static final String DELETE = """
            DELETE FROM person_vote
            WHERE event_id = :eventId AND person_id = :personId AND member_id = :memberId
            """;

    private final JdbcClient jdbc;

    VoteRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // memberId는 로그인하지 않았으면 null
    List<VoteCount> counts(String eventId, Long memberId) {
        return jdbc.sql(COUNTS)
            .param("eventId", eventId)
            .param("memberId", memberId)
            .query(VoteCount.class)
            .list();
    }

    // 그 사건에 없는 인물이면 아무것도 넣지 않고 false를 돌려준다
    boolean save(String eventId, String personId, long memberId, int value) {
        int rows = jdbc.sql(SAVE)
            .param("eventId", eventId)
            .param("personId", personId)
            .param("memberId", memberId)
            .param("value", value)
            .update();
        return rows == 1;
    }

    void delete(String eventId, String personId, long memberId) {
        jdbc.sql(DELETE)
            .param("eventId", eventId)
            .param("personId", personId)
            .param("memberId", memberId)
            .update();
    }
}
