package io.github.crm08134gif.backend.board;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class TargetRepository {

    private static final String EVENT_EXISTS = "SELECT EXISTS (SELECT 1 FROM event WHERE id = :id)";
    private static final String PERSON_EXISTS = "SELECT EXISTS (SELECT 1 FROM person WHERE id = :id)";

    private final JdbcClient jdbc;

    TargetRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    boolean exists(BoardType board, String id) {
        // 테이블 이름은 enum으로만 고른다. 사용자 입력을 SQL 문장에 이어 붙이지 않는다
        String sql = switch(board) {
            case EVENT -> EVENT_EXISTS;
            case PERSON -> PERSON_EXISTS;
        };
        return jdbc.sql(sql)
            .param("id", id)
            .query(Boolean.class)
            .single();
    }
}
