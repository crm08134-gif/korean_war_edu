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

import tools.jackson.databind.DeserializationFeature;
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

    private static final CsvMapper CSV = CsvMapper.builder()
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(CsvReadFeature.EMPTY_STRING_AS_NULL)
        .build();

    record SourceRow(String id, String title, String author, String url, String license) {
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

    private static void check(EventJson e, String fileName) {
        if (!fileName.equals(e.date() + "-" + e.id() + ".json")) {
            throw new IllegalStateException("파일 이름이 <date>-<id>.json 과 다르다");
        }
        if (e.claims() == null || e.claims().isEmpty()) {
            throw new IllegalStateException("주장이 하나도 없다");
        }
        for (EventJson.Claim claim : e.claims()) {
            if (claim.evidence() == null || claim.evidence().isEmpty()) {
                throw new IllegalStateException("근거가 없는 주장이 있다: " + claim.text());
            }
        }
    }

    private void insert(EventJson e) {
        jdbc.sql(INSERT_EVENT).paramSource(e).update();
        for (EventJson.Claim claim : e.claims()) {
            long claimId = jdbc.sql("INSERT INTO event_claim (event_id, text) VALUES (:eventId, :text) RETURNING id")
                .param("eventId", e.id())
                .param("text", claim.text())
                .query(Long.class)
                .single();
            for (EventJson.Evidence evidence : claim.evidence()) {
                jdbc.sql(INSERT_EVIDENCE)
                    .param("claimId", claimId)
                    .param("sourceId", evidence.sourceId())
                    .param("locator", evidence.locator())
                    .param("quote", evidence.quote())
                    .update();
            }
        }
    }
    private static final String INSERT_SOURCE = """
            INSERT INTO source_doc (id, title, author, url, license)
            VALUES (:id, :title, :author, :url, :license)
            """;

    private static final String INSERT_EVENT = """
            INSERT INTO event (id, title, event_date, date_precision, place_name, geom,
                               category, hook, body, confidence_tier, status)
            VALUES (:id, :title, :date, :datePrecision, :placeName,
                    ST_SetSRID(ST_MakePoint(:lon, :lat), 4326),
                    :category, :hook, :body, :confidenceTier, :status)
            """;

    private static final String INSERT_EVIDENCE = """
            INSERT INTO claim_evidence (claim_id, source_id, locator, quote)
            VALUES (:claimId, :sourceId, :locator, :quote)
            """;
}
