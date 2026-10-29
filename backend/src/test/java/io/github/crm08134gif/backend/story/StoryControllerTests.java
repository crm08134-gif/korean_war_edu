package io.github.crm08134gif.backend.story;

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
class StoryControllerTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    void insertStories() {
        jdbc.sql("""
                INSERT INTO story (id, title, summary, body, status)
                VALUES ('test-reviewed', '검수됨', '요약', '[{"type": "paragraph", "text": "본문"}]', 'reviewed'),
                       ('test-draft', '검수 전', '요약', '[{"type": "paragraph", "text": "본문"}]', 'draft')
                """).update();
    }

    @Test
    void listsOnlyReviewedStories() {
        insertStories();

        MvcTestResult result = mvc.get().uri("/api/stories").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$[*].id").asArray()
            .contains("test-reviewed")
            .doesNotContain("test-draft");
    }

    @Test
    void listsBodyAndSourcesAsJson() {
        insertStories();
        // cmh-appleman은 앱이 켜질 때 EventLoader가 sources.csv에서 넣어 둔 출처다
        jdbc.sql("""
                INSERT INTO story_source (story_id, source_id, locator)
                VALUES ('test-reviewed', 'cmh-appleman', '3장')
                """).update();

        MvcTestResult result = mvc.get().uri("/api/stories").exchange();

        assertThat(result).bodyJson().extractingPath("$[?(@.id == 'test-reviewed')].body[0].text").asArray()
            .containsExactly("본문");
        assertThat(result).bodyJson().extractingPath("$[?(@.id == 'test-reviewed')].sources[0].publisher").asArray()
            .containsExactly("미 육군 군사사 센터");
    }

    @Test
    void rejectsCaptionWithoutPhoto() {
        // 대표 사진 설명만 있고 사진이 없으면 DB가 막는다
        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO story (id, title, summary, photo_caption, body, status)
                VALUES ('test-caption', '설명만', '요약', '설명', '[]', 'draft')
                """).update())
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
