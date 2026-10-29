package io.github.crm08134gif.backend.story;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class StoryRepository {

    // 출처와 사진을 모으는 방법은 EventRepository와 같다
    private static final String FIND_ALL = """
            SELECT st.id,
                   st.title,
                   st.summary,
                   st.photo_file,
                   st.photo_caption,
                   st.body,
                   (SELECT json_agg(json_build_object(
                               'publisher', s.publisher,
                               'title', s.title,
                               'url', s.url,
                               'locator', ss.locator)
                           ORDER BY ss.id)
                    FROM story_source ss
                    JOIN source_doc s ON s.id = ss.source_id
                    WHERE ss.story_id = st.id) AS sources,
                   (SELECT COALESCE(json_agg(json_build_object(
                                        'file', p.file,
                                        'title', p.title,
                                        'author', p.author,
                                        'sourceUrl', p.source_url,
                                        'license', p.license,
                                        'licenseUrl', p.license_url,
                                        'modified', p.modified)
                                    ORDER BY p.file), '[]')
                    FROM story_photo sp
                    JOIN photo p ON p.file = sp.file
                    WHERE sp.story_id = st.id) AS photos,
                   st.status
            FROM story st
            WHERE st.status = 'reviewed' OR :includeDrafts
            ORDER BY st.title
            """;

    private final JdbcClient jdbc;

    StoryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<StoryResponse> findAll(boolean includeDrafts) {
        return jdbc.sql(FIND_ALL)
            .param("includeDrafts", includeDrafts)
            .query(StoryResponse.class)
            .list();
    }
}
