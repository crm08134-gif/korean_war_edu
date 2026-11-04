package io.github.crm08134gif.backend.board;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

// 신고는 대상이 글·댓글 두 가지라 엔티티 관계로 잇기 어렵고, 넣고·세고·묶어 보는 일이 대부분이라 SQL로 한다
@Repository
public class ReportRepository {

    private static final String EXISTS = """
            SELECT EXISTS (
                SELECT 1 FROM report
                WHERE target_type = :type AND target_id = :id AND reporter_id = :reporterId)
            """;

    private static final String INSERT = """
            INSERT INTO report (target_type, target_id, reporter_id, reason, detail, created_at)
            VALUES (:type, :id, :reporterId, :reason, :detail, now())
            """;

    // 아직 처리하지 않은 신고 수 = 신고한 서로 다른 회원 수 (한 사람 한 번, report_once_uk)
    private static final String COUNT_OPEN = """
            SELECT count(*) FROM report
            WHERE target_type = :type AND target_id = :id AND handled_at IS NULL
            """;

    // 시각 계산을 DB(now())에 맡긴다. 같은 트랜잭션 안에서 같은 기준 시각을 쓴다
    private static final String COUNT_TODAY = """
            SELECT count(*) FROM report
            WHERE reporter_id = :reporterId AND created_at > now() - interval '1 day'
            """;

    private static final String CLOSE = """
            UPDATE report SET handled_at = now(), handled_by = :adminId, result = :result
            WHERE target_type = :type AND target_id = :id AND handled_at IS NULL
            """;

    // 관리자 큐: 처리 안 된 신고를 대상마다 묶고, 대상의 내용·작성자를 붙인다. 최근 신고 순
    // 관리자는 가려진 글의 내용도 봐야 판단할 수 있으므로 여기서는 숨기지 않는다 (이 주소는 ADMIN만 들어온다)
    private static final String QUEUE = """
            WITH open AS (
                SELECT target_type, target_id,
                       count(*) AS reports,
                       string_agg(DISTINCT reason, ',') AS reasons,
                       string_agg(detail, ' / ') FILTER (WHERE detail IS NOT NULL) AS details,
                       max(created_at) AS last_reported_at
                FROM report
                WHERE handled_at IS NULL
                GROUP BY target_type, target_id
            )
            SELECT o.target_type, o.target_id,
                   COALESCE(p.id, c.post_id) AS post_id,
                   COALESCE(p.status, c.status) AS status,
                   p.title,
                   COALESCE(p.body, c.body) AS body,
                   m.id AS author_id,
                   m.nickname AS author,
                   m.write_banned_until AS author_banned_until,
                   o.reports, o.reasons, o.details, o.last_reported_at
            FROM open o
            LEFT JOIN post p ON o.target_type = 'POST' AND p.id = o.target_id
            LEFT JOIN comment c ON o.target_type = 'COMMENT' AND c.id = o.target_id
            JOIN member m ON m.id = COALESCE(p.author_id, c.author_id)
            ORDER BY o.last_reported_at DESC
            LIMIT 100
            """;

    private final JdbcClient jdbc;

    ReportRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    boolean exists(ReportTarget type, long id, long reporterId) {
        return jdbc.sql(EXISTS)
            .param("type", type.name())
            .param("id", id)
            .param("reporterId", reporterId)
            .query(Boolean.class)
            .single();
    }

    void insert(ReportTarget type, long id, long reporterId, ReportReason reason, String detail) {
        jdbc.sql(INSERT)
            .param("type", type.name())
            .param("id", id)
            .param("reporterId", reporterId)
            .param("reason", reason.name())
            .param("detail", detail)
            .update();
    }

    long countOpen(ReportTarget type, long id) {
        return jdbc.sql(COUNT_OPEN)
            .param("type", type.name())
            .param("id", id)
            .query(Long.class)
            .single();
    }

    long countToday(long reporterId) {
        return jdbc.sql(COUNT_TODAY)
            .param("reporterId", reporterId)
            .query(Long.class)
            .single();
    }

    void close(ReportTarget type, long id, long adminId, PostStatus result) {
        jdbc.sql(CLOSE)
            .param("type", type.name())
            .param("id", id)
            .param("adminId", adminId)
            .param("result", result.name())
            .update();
    }

    List<QueueItem> queue() {
        return jdbc.sql(QUEUE).query(QueueItem.class).list();
    }
}
