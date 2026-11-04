package io.github.crm08134gif.backend.board;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

// 공감은 (글, 회원) 한 쌍뿐이고 바꿀 필드도 동작도 없어서 엔티티 없이 SQL로 한다 (투표와 같은 방식)
@Repository
public class LikeRepository {

    // 보이는 글에만 넣는다. 이미 눌렀으면 아무것도 안 한다 (동시에 두 번 와도 기본 키가 막는다)
    private static final String LIKE_POST = """
            INSERT INTO post_like (post_id, member_id, created_at)
            SELECT p.id, :memberId, now()
            FROM post p
            WHERE p.id = :id AND p.status = 'VISIBLE'
            ON CONFLICT (post_id, member_id) DO NOTHING
            """;

    private static final String UNLIKE_POST = """
            DELETE FROM post_like WHERE post_id = :id AND member_id = :memberId
            """;

    // 여러 글의 공감 수를 한 번에. 공감이 없는 글은 결과에 안 나오므로 0으로 본다
    private static final String POST_COUNTS = """
            SELECT post_id AS id,
                   COUNT(*) AS likes,
                   COALESCE(BOOL_OR(member_id = :memberId), false) AS mine
            FROM post_like
            WHERE post_id IN (:ids)
            GROUP BY post_id
            """;

    private static final String LIKE_COMMENT = """
            INSERT INTO comment_like (comment_id, member_id, created_at)
            SELECT c.id, :memberId, now()
            FROM comment c
            WHERE c.id = :id AND c.status = 'VISIBLE'
            ON CONFLICT (comment_id, member_id) DO NOTHING
            """;

    private static final String UNLIKE_COMMENT = """
            DELETE FROM comment_like WHERE comment_id = :id AND member_id = :memberId
            """;

    private static final String COMMENT_COUNTS = """
            SELECT comment_id AS id,
                   COUNT(*) AS likes,
                   COALESCE(BOOL_OR(member_id = :memberId), false) AS mine
            FROM comment_like
            WHERE comment_id IN (:ids)
            GROUP BY comment_id
            """;

    private final JdbcClient jdbc;

    LikeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // 누름/취소를 나눈다. 같은 요청을 두 번 보내도 결과가 같다 (토글은 빠르게 두 번 누르면 뒤집힌다)
    void likePost(long postId, long memberId) {
        change(LIKE_POST, postId, memberId);
    }

    void unlikePost(long postId, long memberId) {
        change(UNLIKE_POST, postId, memberId);
    }

    void likeComment(long commentId, long memberId) {
        change(LIKE_COMMENT, commentId, memberId);
    }

    void unlikeComment(long commentId, long memberId) {
        change(UNLIKE_COMMENT, commentId, memberId);
    }

    // memberId는 로그인하지 않았으면 null이다. 그때 mine은 모두 false
    Map<Long, LikeCount> postCounts(Collection<Long> postIds, Long memberId) {
        return counts(POST_COUNTS, postIds, memberId);
    }

    Map<Long, LikeCount> commentCounts(Collection<Long> commentIds, Long memberId) {
        return counts(COMMENT_COUNTS, commentIds, memberId);
    }

    private void change(String sql, long id, long memberId) {
        jdbc.sql(sql)
            .param("id", id)
            .param("memberId", memberId)
            .update();
    }

    private Map<Long, LikeCount> counts(String sql, Collection<Long> ids, Long memberId) {
        // IN ()은 SQL 문법 오류라서 빈 목록이면 DB에 묻지 않는다
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<LikeCount> rows = jdbc.sql(sql)
            .param("ids", ids)
            .param("memberId", memberId)
            .query(LikeCount.class)
            .list();
        return rows.stream().collect(Collectors.toMap(LikeCount::id, Function.identity()));
    }
}
