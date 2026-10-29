package io.github.crm08134gif.backend.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
import tools.jackson.dataformat.csv.CsvMapper;
import tools.jackson.dataformat.csv.CsvReadFeature;
import tools.jackson.dataformat.csv.CsvSchema;

// 이야기 적재기(StoryLoader, @Order 2)가 이 적재기가 넣은 출처와 사진을 쓰므로 먼저 돈다
@Component
@Order(1)
public class EventLoader implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(EventLoader.class);
    private static final String DIR = "classpath:events/";
    // 사진 파일은 static/ 아래라 /photos/<파일 이름>으로 바로 내려받을 수 있다. photos.csv는 내보내지 않는다
    private static final String PHOTO_DIR = "classpath:static/photos/";
    private static final String PHOTO_CSV = "classpath:photos/photos.csv";
    // 웹툰 그림도 static/ 아래라 /toons/<파일 이름>으로 내려받는다
    private static final String TOON_DIR = "classpath:static/toons/";

    private static final JsonMapper JSON = JsonMapper.builder()
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();

    // List만 보면 요소가 Block인지 모르므로 알려 준다. 그래야 블록마다 "type"을 다시 써 준다
    private static final ObjectWriter BODY_WRITER = JSON.writerFor(new TypeReference<List<EventJson.Block>>() {
    });

    private static final ObjectWriter TOONS_WRITER = JSON.writerFor(new TypeReference<List<EventJson.Toon>>() {
    });

    private static final CsvMapper CSV = CsvMapper.builder()
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(CsvReadFeature.EMPTY_STRING_AS_NULL)
        .build();

    record SourceRow(String id, String title, String author, String publisher, String url, String license) {
    }

    // data/photos/photos.csv 한 줄 = 사진 파일 하나의 메타데이터
    record PhotoRow(
        @JsonProperty("file") String file,
        @JsonProperty("title") String title,
        @JsonProperty("author") String author,
        @JsonProperty("source_url") String sourceUrl,
        @JsonProperty("license") String license,
        @JsonProperty("license_url") String licenseUrl,
        @JsonProperty("retrieved_on") LocalDate retrievedOn,
        @JsonProperty("modified") String modified
    ) {
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
        // 이야기(story 패키지)는 사진과 출처를 가리켜서, 그 둘을 지우기 전에 먼저 지워야 한다.
        // 다시 넣는 것은 이 적재기 뒤에 도는 StoryLoader가 한다
        jdbc.sql("DELETE FROM story").update();
        // 사건의 출처 목록(event_source)과 사진 목록(event_photo)은 ON DELETE CASCADE로 함께 지워진다
        jdbc.sql("DELETE FROM event").update();
        jdbc.sql("DELETE FROM source_doc").update();
        jdbc.sql("DELETE FROM photo").update();

        List<SourceRow> sources = readCsv(DIR + "sources.csv", SourceRow.class);
        for (SourceRow source : sources) {
            jdbc.sql(INSERT_SOURCE).paramSource(source).update();
        }

        List<PhotoRow> photos = readCsv(PHOTO_CSV, PhotoRow.class);
        checkPhotoFiles(photoNames(photos), fileNames(PHOTO_DIR));
        for (PhotoRow photo : photos) {
            try {
                jdbc.sql(INSERT_PHOTO).paramSource(photo).update();
            } catch (RuntimeException e) {
                throw new IllegalStateException("photos.csv의 " + photo.file() + " 사진을 넣지 못했다: " + e.getMessage(), e);
            }
        }

        Resource[] files = resources.getResources(DIR + "*.json");
        // 사건들이 쓴 웹툰 그림 파일. 다 넣은 뒤 data/toons/의 파일과 맞춰 본다
        Set<String> usedToons = new HashSet<>();
        for (Resource file : files) {
            try (InputStream in = file.getInputStream()) {
                EventJson event = JSON.readValue(in, EventJson.class);
                check(event, file.getFilename());
                insert(event);
                for (EventJson.Toon toon : toons(event)) {
                    usedToons.add(toon.file());
                }
            } catch (RuntimeException e) {
                throw new IllegalStateException(file.getFilename() + " 사건을 넣지 못했다: " + e.getMessage(), e);
            }
        }
        checkToonFiles(usedToons, fileNames(TOON_DIR));
        log.info("사건 {}개, 출처 {}개, 사진 {}개, 웹툰 그림 {}개를 DB에 맞췄다",
            files.length, sources.size(), photos.size(), usedToons.size());
    }

    private <T> List<T> readCsv(String path, Class<T> type) throws IOException {
        CsvSchema schema = CsvSchema.emptySchema().withHeader();
        try (InputStream in = resources.getResource(path).getInputStream()) {
            return CSV.readerFor(type)
                .with(schema)
                .<T>readValues(in)
                .readAll();
        }
    }

    private static Set<String> photoNames(List<PhotoRow> photos) {
        Set<String> names = new HashSet<>();
        for (PhotoRow photo : photos) {
            names.add(photo.file());
        }
        return names;
    }

    // 사진·웹툰 폴더의 파일 이름들
    private Set<String> fileNames(String dir) throws IOException {
        Set<String> names = new HashSet<>();
        // 파일이 하나도 없으면 폴더도 만들어지지 않는다
        if (!resources.getResource(dir).exists()) {
            return names;
        }
        for (Resource file : resources.getResources(dir + "*")) {
            names.add(file.getFilename());
        }
        return names;
    }

    // 사진 파일과 photos.csv 줄이 하나씩 맞아야 한다. 메타데이터(라이선스)가 없는 사진은 앱에 실을 수 없다 (CLAUDE.md 0장)
    static void checkPhotoFiles(Set<String> rows, Set<String> files) {
        for (String file : files) {
            if (!rows.contains(file)) {
                throw new IllegalStateException("photos.csv에 없는 사진 파일이 있다: " + file);
            }
        }
        for (String row : rows) {
            if (!files.contains(row)) {
                throw new IllegalStateException("photos.csv에 있는데 사진 파일이 없다: " + row);
            }
        }
    }

    // 웹툰 그림은 DB 표가 없어(JSON 열) 외래 키 대신 여기서 맞춰 본다.
    // 사건이 쓰는 그림은 파일이 있어야 하고, 어느 사건도 쓰지 않는 그림 파일은 두지 않는다
    static void checkToonFiles(Set<String> used, Set<String> files) {
        for (String file : used) {
            if (!files.contains(file)) {
                throw new IllegalStateException("사건이 쓰는 웹툰 그림 파일이 data/toons/에 없다: " + file);
            }
        }
        for (String file : files) {
            if (!used.contains(file)) {
                throw new IllegalStateException("어느 사건도 쓰지 않는 웹툰 그림 파일이 있다: " + file);
            }
        }
    }

    // toons는 빼도 되므로 null이면 빈 목록으로 본다
    private static List<EventJson.Toon> toons(EventJson e) {
        return e.toons() == null ? List.of() : e.toons();
    }

    // DB 제약으로 막기 어려운 것만 여기서 본다 (빈 칸, 날짜 범위, 종류 값 등은 DB가 막는다)
    private static void check(EventJson e, String fileName) {
        if (!fileName.equals(e.date() + "-" + e.id() + ".json")) {
            throw new IllegalStateException("파일 이름이 <date>-<id>.json 과 다르다");
        }
        checkContent(e.sources(), e.body());
        // JSON 열 안이라 DB의 NOT NULL이 닿지 않는다. alt는 그림 속 글을 화면 낭독기에 전한다
        for (EventJson.Toon toon : toons(e)) {
            if (toon.file() == null || toon.file().isBlank() || toon.alt() == null || toon.alt().isBlank()) {
                throw new IllegalStateException("웹툰 그림에 file이나 alt가 비었다");
            }
        }
    }

    // 사건과 이야기(StoryLoader)가 함께 쓰는 검사: 출처와 본문이 있어야 하고, 본문 블록이 비면 안 된다
    public static void checkContent(List<EventJson.SourceRef> sources, List<EventJson.Block> body) {
        if (sources == null || sources.isEmpty()) {
            throw new IllegalStateException("출처가 하나도 없다");
        }
        if (body == null || body.isEmpty()) {
            throw new IllegalStateException("본문이 없다");
        }
        for (EventJson.Block block : body) {
            switch (block) {
                case EventJson.Heading h -> checkText(h.text());
                case EventJson.Paragraph p -> checkText(p.text());
                case EventJson.Table t -> checkTable(t);
                case EventJson.PhotoBlock p -> checkText(p.caption());
            }
        }
    }

    // JSON 배열 안이라 DB의 NOT NULL이 닿지 않는다
    private static void checkText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("본문에 빈 소제목·문단·사진 설명이 있다");
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
        EventJson.Photo photo = e.photo();
        jdbc.sql(INSERT_EVENT)
            .param("id", e.id())
            .param("title", e.title())
            .param("date", e.date())
            .param("endDate", e.endDate())
            .param("datePrecision", e.datePrecision())
            .param("placeName", e.placeName())
            .param("lon", e.lon())
            .param("lat", e.lat())
            .param("region", e.region())
            .param("kind", e.kind())
            .param("summary", e.summary())
            .param("toons", TOONS_WRITER.writeValueAsString(toons(e)))
            .param("photoFile", photo == null ? null : photo.file())
            .param("photoCaption", photo == null ? null : photo.caption())
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
        for (String file : photoFiles(photo, e.body())) {
            jdbc.sql("INSERT INTO event_photo (event_id, file) VALUES (:eventId, :file)")
                .param("eventId", e.id())
                .param("file", file)
                .update();
        }
    }

    // 이 글이 쓰는 사진: 대표 사진과 본문의 사진 블록 (StoryLoader도 쓴다)
    public static List<String> photoFiles(EventJson.Photo photo, List<EventJson.Block> body) {
        List<String> files = new ArrayList<>();
        if (photo != null) {
            files.add(photo.file());
        }
        for (EventJson.Block block : body) {
            if (block instanceof EventJson.PhotoBlock p) {
                files.add(p.file());
            }
        }
        return files;
    }

    private static final String INSERT_PHOTO = """
            INSERT INTO photo (file, title, author, source_url, license, license_url, retrieved_on, modified)
            VALUES (:file, :title, :author, :sourceUrl, :license, :licenseUrl, :retrievedOn, :modified)
            """;

    private static final String INSERT_SOURCE = """
            INSERT INTO source_doc (id, title, author, publisher, url, license)
            VALUES (:id, :title, :author, :publisher, :url, :license)
            """;

    private static final String INSERT_EVENT = """
            INSERT INTO event (id, title, event_date, end_date, date_precision, place_name, geom, region,
                               kind, summary, toons, photo_file, photo_caption, body, status)
            VALUES (:id, :title, :date, :endDate, :datePrecision, :placeName,
                    -- 좌표가 없으면(null) geom도 null이 된다
                    ST_SetSRID(ST_MakePoint(:lon, :lat), 4326), :region,
                    :kind, :summary, CAST(:toons AS jsonb), :photoFile, :photoCaption, CAST(:body AS jsonb), :status)
            """;

    private static final String INSERT_EVENT_SOURCE = """
            INSERT INTO event_source (event_id, source_id, locator)
            VALUES (:eventId, :sourceId, :locator)
            """;
}
