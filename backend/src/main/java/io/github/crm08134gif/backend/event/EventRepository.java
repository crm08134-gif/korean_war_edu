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
                   e.category,
                   e.hook,
                   e.body,
                   e.confidence_tier,
                   e.status,
                   (SELECT json_agg(json_build_object(
                               'text', c.text,
                               'evidence', (SELECT json_agg(json_build_object(
                                                       'locator', v.locator,
                                                       'quote', v.quote,
                                                       'source', to_json(s))
                                                   ORDER BY v.id)
                                            FROM claim_evidence v
                                            JOIN source_doc s ON s.id = v.source_id
                                            WHERE v.claim_id = c.id))
                           ORDER BY c.id)
                    FROM event_claim c
                    WHERE c.event_id = e.id) AS claims
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
