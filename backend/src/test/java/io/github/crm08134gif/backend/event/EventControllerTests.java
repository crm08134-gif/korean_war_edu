
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

    @Test
    void listsOnlyReviewedEvents() {
        jdbc.sql("""
                INSERT INTO event (id, title, event_date, date_precision, place_name, geom,
                                   category, hook, body, confidence_tier, status)
                VALUES ('test-reviewed', '검수됨', '1950-07-01', 'day', '서울',
                        ST_SetSRID(ST_MakePoint(127, 37.5), 4326),
                        'record', '훅', '본문', 'confirmed', 'reviewed'),
                       ('test-draft', '검수 전', '1950-07-01', 'day', '서울',
                        ST_SetSRID(ST_MakePoint(127, 37.5), 4326),
                        'record', '훅', '본문', 'confirmed', 'draft')
                """).update();

        MvcTestResult result = mvc.get().uri("/api/events").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$[*].id").asArray()
            .contains("test-reviewed")
            .doesNotContain("test-draft");
        assertThat(result).bodyJson().extractingPath("$[*].status").asArray()
            .containsOnly("reviewed");
    }
}

