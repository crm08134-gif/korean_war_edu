package io.github.crm08134gif.backend.event;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class EventRepository {

    private static final String FIND_ALL = """
            SELECT e.id,
                   e.title,
                   e.event_date AS date,
                   e.date_precision,
                   e.place_name,
                   ST_X(e.geom) AS lon,
                   ST_Y(e.geom) AS lat,
                   e.kind,
                   e.summary,
                   e.body,
                   (SELECT json_agg(json_build_object(
                               'publisher', s.publisher,
                               'title', s.title,
                               'url', s.url,
                               'locator', es.locator)
                           ORDER BY es.id)
                    FROM event_source es
                    JOIN source_doc s ON s.id = es.source_id
                    WHERE es.event_id = e.id) AS sources,
                   e.status
            FROM event e
            WHERE e.status = 'reviewed' OR :includeDrafts
            ORDER BY e.event_date, e.id
            """;

    private final JdbcClient jdbc;

    EventRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<EventResponse> findAll(boolean includeDrafts) {
        return jdbc.sql(FIND_ALL)
            .param("includeDrafts", includeDrafts)
            .query(EventResponse.class)
            .list();
    }

}
