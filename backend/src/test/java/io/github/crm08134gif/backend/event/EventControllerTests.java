
package io.github.crm08134gif.backend.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.crm08134gif.backend.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
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

    @Test
    void listsPhotoMetadata() {
        insertEvents();
        jdbc.sql("""
                INSERT INTO photo (file, title, author, source_url, license, retrieved_on)
                VALUES ('test.jpg', '시험 사진', '미 육군', 'https://example.org/test', 'public-domain', '2026-10-26')
                """).update();
        jdbc.sql("UPDATE event SET photo_file = 'test.jpg', photo_caption = '설명' WHERE id = 'test-reviewed'").update();
        jdbc.sql("INSERT INTO event_photo (event_id, file) VALUES ('test-reviewed', 'test.jpg')").update();

        MvcTestResult result = mvc.get().uri("/api/events").exchange();

        assertThat(result).bodyJson().extractingPath("$[?(@.id == 'test-reviewed')].photoFile").asArray()
            .containsExactly("test.jpg");
        assertThat(result).bodyJson().extractingPath("$[?(@.id == 'test-reviewed')].photos[0].license").asArray()
            .containsExactly("public-domain");
    }

    @Test
    void rejectsLicenseOutsideAllowList() {
        // 비상업(NC) 조건은 허용 목록에 없다 (CLAUDE.md 0장)
        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO photo (file, title, author, source_url, license, license_url, retrieved_on)
                VALUES ('nc.jpg', '비상업', '누군가', 'https://example.org/nc', 'cc-by-nc-4.0',
                        'https://creativecommons.org/licenses/by-nc/4.0/', '2026-10-26')
                """).update())
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void listsEndDate() {
        insertEvents();
        jdbc.sql("UPDATE event SET end_date = '1950-07-03' WHERE id = 'test-reviewed'").update();

        MvcTestResult result = mvc.get().uri("/api/events").exchange();

        assertThat(result).bodyJson().extractingPath("$[?(@.id == 'test-reviewed')].endDate").asArray()
            .containsExactly("1950-07-03");
    }

    @Test
    void rejectsEndDateNotAfterStart() {
        insertEvents();
        // 시험 사건의 시작일은 1950-07-01이다
        assertThatThrownBy(() -> jdbc.sql("UPDATE event SET end_date = '1950-07-01' WHERE id = 'test-reviewed'").update())
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}

