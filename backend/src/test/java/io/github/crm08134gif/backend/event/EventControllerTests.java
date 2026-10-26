
package io.github.crm08134gif.backend.event;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.crm08134gif.backend.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional

class EventControllerTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    void insertEvents() {
        jdbc.sql("""
                INSERT INTO event (id, title, event_date, date_precision, place_name, geom,
                                   kind, summary, body, status)
                VALUES ('test-reviewed', '검수됨', '1950-07-01', 'day', '서울',
                        ST_SetSRID(ST_MakePoint(127, 37.5), 4326),
                        'battle', '요약', '[{"type": "paragraph", "text": "본문"}]', 'reviewed'),
                       ('test-draft', '검수 전', '1950-07-01', 'day', '서울',
                        ST_SetSRID(ST_MakePoint(127, 37.5), 4326),
                        'battle', '요약', '[{"type": "paragraph", "text": "본문"}]', 'draft')
                """).update();
    }

    @Test
    void listsOnlyReviewedEvents() {
        insertEvents();

        MvcTestResult result = mvc.get().uri("/api/events").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$[*].id").asArray()
            .contains("test-reviewed")
            .doesNotContain("test-draft");
        assertThat(result).bodyJson().extractingPath("$[*].status").asArray()
            .containsOnly("reviewed");
    }

    @Test
    void listsBodyAndSourcesAsJson() {
        insertEvents();
        // cmh-appleman은 앱이 켜질 때 적재기가 sources.csv에서 넣어 둔 출처다
        jdbc.sql("""
                INSERT INTO event_source (event_id, source_id, locator)
                VALUES ('test-reviewed', 'cmh-appleman', '3장')
                """).update();

        MvcTestResult result = mvc.get().uri("/api/events").exchange();

        assertThat(result).bodyJson().extractingPath("$[?(@.id == 'test-reviewed')].body[0].text").asArray()
            .containsExactly("본문");
        assertThat(result).bodyJson().extractingPath("$[?(@.id == 'test-reviewed')].sources[0].publisher").asArray()
            .containsExactly("미 육군 군사사 센터");
        assertThat(result).bodyJson().extractingPath("$[?(@.id == 'test-reviewed')].sources[0].locator").asArray()
            .containsExactly("3장");
    }
}

