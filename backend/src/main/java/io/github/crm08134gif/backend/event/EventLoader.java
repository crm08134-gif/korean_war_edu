package io.github.crm08134gif.backend.event;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.csv.CsvMapper;
import tools.jackson.dataformat.csv.CsvReadFeature;
import tools.jackson.dataformat.csv.CsvSchema;

@Component
public class EventLoader implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(EventLoader.class);
    private static final String DIR = "classpath:events/";

    private static final JsonMapper JSON = JsonMapper.builder()
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();

    // List만 보면 요소가 Block인지 모르므로 알려 준다. 그래야 블록마다 "type"을 다시 써 준다
    private static final ObjectWriter BODY_WRITER = JSON.writerFor(new TypeReference<List<EventJson.Block>>() {
    });

    private static final CsvMapper CSV = CsvMapper.builder()
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(CsvReadFeature.EMPTY_STRING_AS_NULL)
        .build();

    record SourceRow(String id, String title, String author, String publisher, String url, String license) {
    }

    private final ResourcePatternResolver resources;
    private final JdbcClient jdbc;

    EventLoader(ResourcePatternResolver resources, JdbcClient jdbc) {
        this.resources = resources;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) throws IOException {
        // 사건의 출처 목록(event_source)은 ON DELETE CASCADE로 함께 지워진다
        jdbc.sql("DELETE FROM event").update();
        jdbc.sql("DELETE FROM source_doc").update();

        List<SourceRow> sources = readSources();
        for (SourceRow source : sources) {
            jdbc.sql(INSERT_SOURCE).paramSource(source).update();
        }

        Resource[] files = resources.getResources(DIR + "*.json");
        for (Resource file : files) {
            try (InputStream in = file.getInputStream()) {
                EventJson event = JSON.readValue(in, EventJson.class);
                check(event, file.getFilename());
                insert(event);
            } catch (RuntimeException e) {
                throw new IllegalStateException(file.getFilename() + " 사건을 넣지 못했다: " + e.getMessage(), e);
            }
        }
        log.info("사건 {}개, 출처 {}개를 DB에 맞췄다", files.length, sources.size());
    }

    private List<SourceRow> readSources() throws IOException {
        CsvSchema schema = CsvSchema.emptySchema().withHeader();
        try (InputStream in = resources.getResource(DIR + "sources.csv").getInputStream()) {
            return CSV.readerFor(SourceRow.class)
                .with(schema)
                .<SourceRow>readValues(in)
                .readAll();
        }
    }

    // DB 제약으로 막기 어려운 것만 여기서 본다 (빈 칸, 날짜 범위, 종류 값 등은 DB가 막는다)
    private static void check(EventJson e, String fileName) {
        if (!fileName.equals(e.date() + "-" + e.id() + ".json")) {
            throw new IllegalStateException("파일 이름이 <date>-<id>.json 과 다르다");
        }
        if (e.sources() == null || e.sources().isEmpty()) {
            throw new IllegalStateException("출처가 하나도 없다");
        }
        if (e.body() == null || e.body().isEmpty()) {
            throw new IllegalStateException("본문이 없다");
        }
        for (EventJson.Block block : e.body()) {
            switch (block) {
                case EventJson.Heading h -> checkText(h.text());
                case EventJson.Paragraph p -> checkText(p.text());
                case EventJson.Table t -> checkTable(t);
            }
        }
    }

    // JSON 배열 안이라 DB의 NOT NULL이 닿지 않는다
    private static void checkText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("본문에 빈 소제목이나 문단이 있다");
        }
    }

    private static void checkTable(EventJson.Table table) {
        if (table.columns() == null || table.rows() == null || table.rows().isEmpty()) {
            throw new IllegalStateException("표에 칸 이름이나 줄이 없다: " + table.caption());
        }
        for (List<String> row : table.rows()) {
            if (row.size() != table.columns().size()) {
                throw new IllegalStateException("표의 줄과 칸 이름의 칸 수가 다르다: " + table.caption() + " " + row);
            }
        }
    }

    private void insert(EventJson e) {
        jdbc.sql(INSERT_EVENT)
            .param("id", e.id())
            .param("title", e.title())
            .param("date", e.date())
            .param("datePrecision", e.datePrecision())
            .param("placeName", e.placeName())
            .param("lon", e.lon())
            .param("lat", e.lat())
            .param("kind", e.kind())
            .param("summary", e.summary())
            // 블록 목록을 다시 JSON 글자로 바꿔 jsonb 열에 넣는다
            .param("body", BODY_WRITER.writeValueAsString(e.body()))
            .param("status", e.status())
            .update();
        for (EventJson.SourceRef source : e.sources()) {
            jdbc.sql(INSERT_EVENT_SOURCE)
                .param("eventId", e.id())
                .param("sourceId", source.sourceId())
                .param("locator", source.locator())
                .update();
        }
    }

    private static final String INSERT_SOURCE = """
            INSERT INTO source_doc (id, title, author, publisher, url, license)
            VALUES (:id, :title, :author, :publisher, :url, :license)
            """;

    private static final String INSERT_EVENT = """
            INSERT INTO event (id, title, event_date, date_precision, place_name, geom,
                               kind, summary, body, status)
            VALUES (:id, :title, :date, :datePrecision, :placeName,
                    ST_SetSRID(ST_MakePoint(:lon, :lat), 4326),
                    :kind, :summary, CAST(:body AS jsonb), :status)
            """;

    private static final String INSERT_EVENT_SOURCE = """
            INSERT INTO event_source (event_id, source_id, locator)
            VALUES (:eventId, :sourceId, :locator)
            """;
}
