package io.github.crm08134gif.backend.story;

import io.github.crm08134gif.backend.event.EventJson;
import io.github.crm08134gif.backend.event.EventLoader;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

// 앱이 켜질 때 data/stories/의 이야기를 DB에 넣는다.
// 이야기는 EventLoader가 넣는 출처(source_doc)와 사진(photo)을 가리키므로 그 뒤(@Order 2)에 돈다.
// 있던 이야기는 EventLoader가 사진을 지우기 전에 먼저 지웠다
@Component
@Order(2)
public class StoryLoader implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(StoryLoader.class);
    private static final String DIR = "classpath:stories/";

    private static final JsonMapper JSON = JsonMapper.builder()
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();

    // List만 보면 요소가 Block인지 모르므로 알려 준다. 그래야 블록마다 "type"을 다시 써 준다
    private static final ObjectWriter BODY_WRITER = JSON.writerFor(new TypeReference<List<EventJson.Block>>() {
    });

    private final ResourcePatternResolver resources;
    private final JdbcClient jdbc;

    StoryLoader(ResourcePatternResolver resources, JdbcClient jdbc) {
        this.resources = resources;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) throws IOException {
        // 아직 이야기가 하나도 없으면 폴더도 jar에 만들어지지 않는다
        if (!resources.getResource(DIR).exists()) {
            log.info("이야기가 아직 없다");
            return;
        }
        Resource[] files = resources.getResources(DIR + "*.json");
        for (Resource file : files) {
            try (InputStream in = file.getInputStream()) {
                StoryJson story = JSON.readValue(in, StoryJson.class);
                check(story, file.getFilename());
                insert(story);
            } catch (RuntimeException e) {
                throw new IllegalStateException(file.getFilename() + " 이야기를 넣지 못했다: " + e.getMessage(), e);
            }
        }
        log.info("이야기 {}개를 DB에 맞췄다", files.length);
    }

    private static void check(StoryJson s, String fileName) {
        if (!fileName.equals(s.id() + ".json")) {
            throw new IllegalStateException("파일 이름이 <id>.json 과 다르다");
        }
        // 출처·본문 검사는 사건과 같다
        EventLoader.checkContent(s.sources(), s.body());
    }

    private void insert(StoryJson s) {
        EventJson.Photo photo = s.photo();
        jdbc.sql(INSERT_STORY)
            .param("id", s.id())
            .param("title", s.title())
            .param("summary", s.summary())
            .param("photoFile", photo == null ? null : photo.file())
            .param("photoCaption", photo == null ? null : photo.caption())
            // 블록 목록을 다시 JSON 글자로 바꿔 jsonb 열에 넣는다
            .param("body", BODY_WRITER.writeValueAsString(s.body()))
            .param("status", s.status())
            .update();
        for (EventJson.SourceRef source : s.sources()) {
            jdbc.sql("INSERT INTO story_source (story_id, source_id, locator) VALUES (:storyId, :sourceId, :locator)")
                .param("storyId", s.id())
                .param("sourceId", source.sourceId())
                .param("locator", source.locator())
                .update();
        }
        for (String file : EventLoader.photoFiles(photo, s.body())) {
            jdbc.sql("INSERT INTO story_photo (story_id, file) VALUES (:storyId, :file)")
                .param("storyId", s.id())
                .param("file", file)
                .update();
        }
    }

    private static final String INSERT_STORY = """
            INSERT INTO story (id, title, summary, photo_file, photo_caption, body, status)
            VALUES (:id, :title, :summary, :photoFile, :photoCaption, CAST(:body AS jsonb), :status)
            """;
}
