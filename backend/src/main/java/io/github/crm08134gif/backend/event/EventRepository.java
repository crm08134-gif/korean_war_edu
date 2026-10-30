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
                   e.end_date,
                   e.date_precision,
                   e.place_name,
                   ST_X(e.geom) AS lon,
                   ST_Y(e.geom) AS lat,
                   e.region,
                   e.kind,
                   e.summary,
                   e.photo_file,
                   e.photo_caption,
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
                   -- 대표 사진과 본문 사진의 출처 표기에 쓰는 메타데이터. 사진이 없으면 빈 배열
                   (SELECT COALESCE(json_agg(json_build_object(
                                        'file', p.file,
                                        'title', p.title,
                                        'author', p.author,
                                        'sourceUrl', p.source_url,
                                        'license', p.license,
                                        'licenseUrl', p.license_url,
                                        'modified', p.modified)
                                    ORDER BY p.file), '[]')
                    FROM event_photo ep
                    JOIN photo p ON p.file = ep.file
                    WHERE ep.event_id = e.id) AS photos,
                    (SELECT COALESCE(json_agg(json_build_object(
                                                        'id', pe.id,
                                                        'name', pe.name,
                                                        'nameOriginal', pe.name_original,
                                                        'country', pe.country,
                                                        'side', pe.side,
                                                        'role', ep.role,
                                                        'description', ep.description,
                                                        'photo', CASE WHEN p.file IS NULL THEN NULL
                                                                      ELSE json_build_object(
                                                                          'file', p.file,
                                                                          'title', p.title,
                                                                          'author', p.author,
                                                                          'sourceUrl', p.source_url,
                                                                          'license', p.license,
                                                                          'licenseUrl', p.license_url,
                                                                          'modified', p.modified)
                                                                 END,
                                                        'sources', (SELECT json_agg(json_build_object(
                                                                               'publisher', s.publisher,
                                                                               'title', s.title,
                                                                               'url', s.url,
                                                                               'locator', eps.locator)
                                                                           ORDER BY eps.id)
                                                                    FROM event_person_source eps
                                                                    JOIN source_doc s ON s.id = eps.source_id
                                                                    WHERE eps.event_id = ep.event_id
                                                                      AND eps.person_id = ep.person_id))
                                                    ORDER BY ep.position), '[]')
                                    FROM event_person ep
                                    JOIN person pe ON pe.id = ep.person_id
                                    LEFT JOIN photo p ON p.file = pe.photo_file
                                    WHERE ep.event_id = e.id
                                      AND (ep.status = 'reviewed' OR :includeDrafts)) AS people,
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
