package io.github.crm08134gif.backend.frontline;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class FrontlineSnapshotRepository {

    private static final String FIND_ALL = """
            SELECT s.snapshot_date,
                   s.source_map,
                   s.note,
                   ST_AsGeoJSON(r.line, 6) AS front_line,
                   ST_AsGeoJSON(s.extra_areas, 6) AS extra_areas
            FROM frontline_snapshot s
            CROSS JOIN LATERAL (
                SELECT ST_MakeLine(ST_LineInterpolatePoint(s.front_line, i / (:points - 1.0)) ORDER BY i) AS line
                FROM generate_series(0, :points - 1) AS i
            ) AS r
            ORDER BY s.snapshot_date
            """;

    private final JdbcClient jdbc;

    FrontlineSnapshotRepository(JdbcClient jdbc){
        this.jdbc = jdbc;
    }

    List<SnapshotResponse> findAll(int points){
        return jdbc.sql(FIND_ALL)
            .param("points", points)
            .query(SnapshotResponse.class)
            .list();
    }

}
