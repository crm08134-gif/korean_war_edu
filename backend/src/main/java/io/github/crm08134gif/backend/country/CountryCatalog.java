package io.github.crm08134gif.backend.country;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.dataformat.csv.CsvMapper;
import tools.jackson.dataformat.csv.CsvReadFeature;
import tools.jackson.dataformat.csv.CsvSchema;

// 참전국·의료지원국 목록. 바뀌지 않고 다른 표와 이어지지도 않아서 DB에 넣지 않는다.
// 앱이 켜질 때 CSV를 한 번 읽고 검사해 메모리에 둔다. 검사에 걸리면 앱이 뜨지 않는다
@Component
class CountryCatalog {

    private static final String CSV_PATH = "classpath:countries/countries.csv";

    private static final CsvMapper CSV = CsvMapper.builder()
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(CsvReadFeature.EMPTY_STRING_AS_NULL)
        .build();

    private final List<CountryRow> rows;

    CountryCatalog(ResourceLoader resources) throws IOException {
        CsvSchema schema = CsvSchema.emptySchema().withHeader();
        try (InputStream in = resources.getResource(CSV_PATH).getInputStream()) {
            this.rows = CSV.readerFor(CountryRow.class).with(schema).<CountryRow>readValues(in).readAll();
        }
        check(rows);
    }

    // 파일 순서(국가보훈부 표의 순서) 그대로 돌려준다. 검수 전(draft)은 설정이 켜졌을 때만
    List<CountryResponse> list(boolean includeDrafts) {
        List<CountryResponse> result = new ArrayList<>();
        for (CountryRow row : rows) {
            if (!includeDrafts && !"reviewed".equals(row.status())) {
                continue;
            }
            result.add(new CountryResponse(row.id(), row.name(), row.group(), row.flag(),
                    row.personnel(), row.killed(), row.casualties(), row.unit(), row.note()));
        }
        return result;
    }

    // DB가 없으니 DB 제약이 할 검사를 여기서 한다
    static void check(List<CountryRow> rows) {
        Set<String> ids = new HashSet<>();
        for (CountryRow row : rows) {
            String where = "countries.csv의 " + row.id() + ": ";
            if (row.id() == null || !row.id().matches("[a-z]{2}")) {
                throw new IllegalStateException(where + "id는 소문자 두 글자 나라 코드다");
            }
            if (!ids.add(row.id())) {
                throw new IllegalStateException(where + "id가 두 번 나온다");
            }
            if (row.name() == null) {
                throw new IllegalStateException(where + "이름이 없다");
            }
            // 국기 파일 이름은 주소에 들어가므로 글자를 좁게 받는다
            if (row.flag() != null && !row.flag().matches("[a-z]{2}")) {
                throw new IllegalStateException(where + "flag는 소문자 두 글자다");
            }
            if (!"draft".equals(row.status()) && !"reviewed".equals(row.status())) {
                throw new IllegalStateException(where + "status는 draft 또는 reviewed다");
            }
            if (row.personnel() != null && row.personnel() <= 0) {
                throw new IllegalStateException(where + "참전 인원은 0보다 커야 한다");
            }
            switch (row.group() == null ? "" : row.group()) {
                case "combat" -> checkCombat(row, where);
                case "medical" -> checkMedical(row, where);
                default -> throw new IllegalStateException(where + "group은 combat 또는 medical이다");
            }
        }
    }

    // 전투병 파견국은 세 숫자가 모두 있어야 하고, 전사자는 전체 사상자에 들어 있다
    private static void checkCombat(CountryRow row, String where) {
        if (row.personnel() == null || row.killed() == null || row.casualties() == null) {
            throw new IllegalStateException(where + "참전국은 참전 인원·전사자·사상자가 모두 있어야 한다");
        }
        if (row.killed() < 0 || row.killed() > row.casualties()) {
            throw new IllegalStateException(where + "전사자가 사상자보다 많다");
        }
        if (row.unit() != null) {
            throw new IllegalStateException(where + "unit은 의료지원국에만 쓴다");
        }
    }

    // 의료지원국은 규모를 모르면 비워 둔다. 사상자 칸은 쓰지 않는다
    private static void checkMedical(CountryRow row, String where) {
        if (row.killed() != null || row.casualties() != null) {
            throw new IllegalStateException(where + "의료지원국에는 전사자·사상자 칸을 쓰지 않는다");
        }
    }
}
