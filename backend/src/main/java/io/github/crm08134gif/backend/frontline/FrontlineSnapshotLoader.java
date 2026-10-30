package io.github.crm08134gif.backend.frontline;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.dataformat.csv.CsvMapper;
import tools.jackson.dataformat.csv.CsvReadFeature;
import tools.jackson.dataformat.csv.CsvSchema;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@Component
public class FrontlineSnapshotLoader implements ApplicationRunner{
    private static final Logger log = LoggerFactory.getLogger(FrontlineSnapshotLoader.class);
    private static final String DIR = "classpath:frontlines/";

    private static final CsvMapper CSV = CsvMapper.builder()
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(CsvReadFeature.EMPTY_STRING_AS_NULL)
        .build();

    private final ResourcePatternResolver resources;
    private final JdbcClient jdbc;

    FrontlineSnapshotLoader(ResourcePatternResolver resources, JdbcClient jdbc){
        this.resources = resources;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) throws IOException{
        List<SnapshotCsvRow> rows = readCsv();
        checkStrayFiles(rows);
        for (SnapshotCsvRow row : rows){
            upsert(row);
        }
        List<LocalDate> dates = rows.stream().map(SnapshotCsvRow::snapshotDate).toList();
        int deleted = jdbc.sql("DELETE FROM frontline_snapshot WHERE snapshot_date NOT IN (:dates)")
            .param("dates", dates)
            .update();
        log.info("전선 스냅샷 {}개를 DB에 맞췄다 (지운 날짜 {}개): {}", rows.size(), deleted, dates);
    }

    private List<SnapshotCsvRow> readCsv() throws IOException {
        CsvSchema schema = CsvSchema.emptySchema().withHeader();
        try (InputStream in = resources.getResource(DIR + "snapshots.csv").getInputStream()) {
            List<SnapshotCsvRow> rows = CSV.readerFor(SnapshotCsvRow.class)
                .with(schema)
                .<SnapshotCsvRow>readValues(in)
                .readAll();
            if (rows.isEmpty()) {
                throw new IllegalStateException("snapshots.csv에 스냅샷이 하나도 없다");
            }
            Set<LocalDate> seen = new HashSet<>();
            for (SnapshotCsvRow row : rows) {
                if (row.snapshotDate() == null || row.title() == null || row.sourceMap() == null) {
                    throw new IllegalStateException("snapshots.csv에 날짜·이름·근거 지도 중 빈 칸이 있다: " + row);
                }
                if (!seen.add(row.snapshotDate())){
                    throw new IllegalStateException("snapshots.csv에 같은 날짜가 두 번 있다: " + row.snapshotDate());
                }
            }
            return rows;
        }
    }

    private void upsert(SnapshotCsvRow row) throws IOException{
        LocalDate date = row.snapshotDate();
        String frontLine = readOrNull(date + ".front_line.geojson");
        String extraAreas = readOrNull(date + ".extra_areas.geojson");
        try{
            jdbc.sql(UPSERT)
                .param("date", date)
                .param("title", row.title())
                .param("frontLine", frontLine)
                .param("extraAreas", extraAreas)
                .param("sourceMap", row.sourceMap())
                .param("note", row.note())
                .update();
        } catch (DataAccessException e){
            throw new IllegalStateException(date + " 스냅샷을 DB에 넣지 못했다", e);
        }
    }

    private String readOrNull(String fileName) throws IOException{
        Resource file = resources.getResource(DIR + fileName);
        return file.exists() ? file.getContentAsString(StandardCharsets.UTF_8) : null;
    }

    private void checkStrayFiles(List<SnapshotCsvRow> rows) throws IOException{
        Set<String> allowed = new HashSet<>();
        for (SnapshotCsvRow row : rows){
            allowed.add(row.snapshotDate() + ".front_line.geojson");
            allowed.add(row.snapshotDate() + ".extra_areas.geojson");
        }
        for (Resource file : resources.getResources(DIR + "*.geojson")){
            if (!allowed.contains(file.getFilename())){
                throw new IllegalStateException("snapshots.csv에 없는 날짜의 파일이다: " + file.getFilename());
            }
        }
    }

    private static final String UPSERT = """
            INSERT INTO frontline_snapshot (snapshot_date, title, front_line, extra_areas, source_map, note)
            VALUES (
                :date,
                :title,
                (SELECT ST_GeomFromGeoJSON(f -> 'geometry')
                   FROM jsonb_array_elements(CAST(:frontLine AS jsonb) -> 'features') AS f),
                (SELECT ST_Multi(ST_Collect(ST_GeomFromGeoJSON(f -> 'geometry')))
                   FROM jsonb_array_elements(CAST(:extraAreas AS jsonb) -> 'features') AS f),
                :sourceMap,
                :note
            )
            ON CONFLICT (snapshot_date) DO UPDATE SET
                title = EXCLUDED.title,
                front_line = EXCLUDED.front_line,
                extra_areas = EXCLUDED.extra_areas,
                source_map = EXCLUDED.source_map,
                note = EXCLUDED.note
            """;

}
