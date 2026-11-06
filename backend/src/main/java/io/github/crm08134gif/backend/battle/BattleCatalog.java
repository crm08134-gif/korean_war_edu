package io.github.crm08134gif.backend.battle;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

// 전투 재생 데이터. 다른 표와 이어지지 않고 바뀌지 않아서 DB에 넣지 않는다 (참전국과 같은 방식).
// 앱이 켜질 때 data/battles/*.json을 읽고 검사해 메모리에 둔다. 검사에 걸리면 앱이 뜨지 않는다.
// 사건이 정말 전투인지, 날짜가 사건 기간 안인지는 EventLoader(@Order 1)가 넣은 DB를 보고 확인하므로 그 뒤에 돈다
@Component
@Order(3)
class BattleCatalog implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BattleCatalog.class);

    // classpath*: 는 폴더가 없을 때(아직 전투 데이터가 없을 때) 오류 대신 빈 목록을 준다
    private static final String DIR = "classpath*:battles/";
    private static final int MAX_UNITS = 12;
    private static final int MIN_KEYFRAMES = 2;
    private static final int MAX_KEYFRAMES = 6;
    private static final int MAX_CAPTION = 80;
    // 폰 화면에서 한 날짜에 화살표가 이보다 많으면 알아보기 어렵다
    private static final int MAX_ARROWS = 8;
    // 첫 날짜는 전투 직전의 배치를 보이려고 사건 시작일보다 이만큼까지 앞서도 된다 (다부동: CMH 지도의 8. 5. 배치)
    private static final int MAX_DAYS_BEFORE_START = 14;

    private static final JsonMapper JSON = JsonMapper.builder()
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();

    // 사건 종류와 기간. 종료일이 없는 사건은 시작일 하루짜리다
    private static final String EVENT = """
            SELECT kind, event_date AS start_date, COALESCE(end_date, event_date) AS end_date
            FROM event
            WHERE id = :id
            """;

    private final ResourcePatternResolver resources;
    private final JdbcClient jdbc;
    private List<BattleJson> battles = List.of();

    BattleCatalog(ResourcePatternResolver resources, JdbcClient jdbc) {
        this.resources = resources;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        List<BattleJson> loaded = new ArrayList<>();
        for (Resource file : resources.getResources(DIR + "*.json")) {
            try (InputStream in = file.getInputStream()) {
                BattleJson battle = JSON.readValue(in, BattleJson.class);
                check(battle, file.getFilename());
                checkEvent(battle, findEvent(battle.eventId()));
                loaded.add(battle);
            } catch (RuntimeException e) {
                throw new IllegalStateException(file.getFilename() + " 전투 재생을 읽지 못했다: " + e.getMessage(), e);
            }
        }
        this.battles = loaded;
        log.info("전투 재생 {}개를 읽었다", loaded.size());
    }

    // 검수 전(draft)은 설정이 켜졌을 때만 내보낸다
    List<BattleResponse> list(boolean includeDrafts) {
        List<BattleResponse> result = new ArrayList<>();
        for (BattleJson battle : battles) {
            if (!includeDrafts && !"reviewed".equals(battle.status())) {
                continue;
            }
            // showFront를 적지 않은 파일은 전선을 보인다
            boolean showFront = battle.showFront() == null || battle.showFront();
            result.add(new BattleResponse(battle.eventId(), battle.view(), battle.maps(), battle.units(), battle.keyframes(), showFront));
        }
        return result;
    }

    // 사건이 없으면 null
    private EventInfo findEvent(String id) {
        return jdbc.sql(EVENT)
                .param("id", id)
                // 열 이름(kind, start_date, end_date)이 EventInfo 칸 이름(kind, startDate, endDate)과 맞아 그대로 담긴다
                .query(EventInfo.class)
                .optional()
                .orElse(null);
    }

    record EventInfo(String kind, LocalDate startDate, LocalDate endDate) {
    }

    // DB가 할 수 없는 형식 검사. 테스트에서 따로 부르려고 static으로 둔다
    static void check(BattleJson b, String fileName) {
        if (b.eventId() == null || !(b.eventId() + ".json").equals(fileName)) {
            throw new IllegalStateException("파일 이름은 <eventId>.json이어야 한다");
        }
        if (!"draft".equals(b.status()) && !"reviewed".equals(b.status())) {
            throw new IllegalStateException("status는 draft 또는 reviewed다");
        }
        BattleJson.View v = b.view();
        if (v == null || v.west() >= v.east() || v.south() >= v.north()) {
            throw new IllegalStateException("view는 west < east, south < north여야 한다");
        }
        if (b.maps() == null || b.maps().isEmpty() || b.maps().size() > 4) {
            throw new IllegalStateException("근거 지도(maps)는 1~4장이다");
        }
        for (BattleJson.MapRef map : b.maps()) {
            if (map.title() == null || map.title().isBlank()) {
                throw new IllegalStateException("근거 지도에 title이 없다");
            }
        }

        // 부대: id 중복 없음, 국기 이름과 진영 형식
        if (b.units() == null || b.units().isEmpty() || b.units().size() > MAX_UNITS) {
            throw new IllegalStateException("부대는 1~" + MAX_UNITS + "개다");
        }
        Set<String> unitIds = new HashSet<>();
        for (BattleJson.Unit unit : b.units()) {
            if (unit.id() == null || !unit.id().matches("[a-z0-9-]{1,40}")) {
                throw new IllegalStateException("부대 id는 소문자·숫자·-로 40자까지다: " + unit.id());
            }
            if (!unitIds.add(unit.id())) {
                throw new IllegalStateException("부대 id가 두 번 나온다: " + unit.id());
            }
            if (unit.name() == null || unit.name().isBlank() || unit.name().length() > 20) {
                throw new IllegalStateException(unit.id() + ": 부대 이름은 1~20자다");
            }
            // 국기 파일 이름이 주소에 들어가므로 글자를 좁게 받는다
            if (unit.country() == null || !unit.country().matches("[a-z]{2}")) {
                throw new IllegalStateException(unit.id() + ": country는 소문자 두 글자다");
            }
            if (!"un".equals(unit.side()) && !"communist".equals(unit.side())) {
                throw new IllegalStateException(unit.id() + ": side는 un 또는 communist다");
            }
        }

        // 키프레임: 개수, 날짜 순서, 설명 길이, 좌표가 view 안
        if (b.keyframes() == null || b.keyframes().size() < MIN_KEYFRAMES || b.keyframes().size() > MAX_KEYFRAMES) {
            throw new IllegalStateException("키프레임은 " + MIN_KEYFRAMES + "~" + MAX_KEYFRAMES + "개다");
        }
        BattleJson.Keyframe first = b.keyframes().get(0);
        if (first.arrows() != null && !first.arrows().isEmpty()) {
            throw new IllegalStateException(first.date() + ": 첫 날짜에는 화살표를 달지 않는다 (앞 날짜부터의 움직임이라서)");
        }
        LocalDateTime previous = null;
        for (BattleJson.Keyframe k : b.keyframes()) {
            LocalDateTime date = parseKeyDate(k.date());
            if (previous != null && !date.isAfter(previous)) {
                throw new IllegalStateException(k.date() + ": 키프레임 날짜는 앞 날짜보다 뒤여야 한다");
            }
            previous = date;
            if (k.caption() == null || k.caption().isBlank() || k.caption().length() > MAX_CAPTION) {
                throw new IllegalStateException(k.date() + ": caption은 1~" + MAX_CAPTION + "자다");
            }
            if (k.front() != null) {
                if (k.front().size() < 2) {
                    throw new IllegalStateException(k.date() + ": 전선 조각은 점이 2개 이상이다");
                }
                for (List<Double> point : k.front()) {
                    checkPoint(point, v, k.date() + " 전선");
                }
            }
            if (k.positions() == null || k.positions().isEmpty()) {
                throw new IllegalStateException(k.date() + ": 그날 있는 부대가 하나도 없다");
            }
            for (Map.Entry<String, List<Double>> entry : k.positions().entrySet()) {
                if (!unitIds.contains(entry.getKey())) {
                    throw new IllegalStateException(k.date() + ": units에 없는 부대다: " + entry.getKey());
                }
                checkPoint(entry.getValue(), v, k.date() + " " + entry.getKey());
            }
            checkArrows(k, v);
        }
    }

    // 화살표: 개수, 진영, 점 2개 이상, 좌표가 view 안. 첫 날짜는 앞 날짜가 없어 화살표를 달 수 없다
    private static void checkArrows(BattleJson.Keyframe k, BattleJson.View v) {
        if (k.arrows() == null) {
            return;
        }
        if (k.arrows().size() > MAX_ARROWS) {
            throw new IllegalStateException(k.date() + ": 화살표는 날짜마다 " + MAX_ARROWS + "개까지다");
        }
        for (BattleJson.Arrow arrow : k.arrows()) {
            if (!"un".equals(arrow.side()) && !"communist".equals(arrow.side())) {
                throw new IllegalStateException(k.date() + ": 화살표 side는 un 또는 communist다");
            }
            if (arrow.path() == null || arrow.path().size() < 2) {
                throw new IllegalStateException(k.date() + ": 화살표는 점이 2개 이상이다");
            }
            for (List<Double> point : arrow.path()) {
                checkPoint(point, v, k.date() + " 화살표");
            }
        }
    }

    // 키프레임 날짜: "1950-08-13" 또는 하루짜리 전투처럼 시각까지 "1950-07-05T08:00". 시각이 없으면 그날 0시로 본다
    static LocalDateTime parseKeyDate(String text) {
        if (text == null) {
            throw new IllegalStateException("키프레임에 date가 없다");
        }
        try {
            if (text.length() == 10) {
                return LocalDate.parse(text).atStartOfDay();
            }
            if (text.length() == 16) {
                return LocalDateTime.parse(text);
            }
        } catch (DateTimeParseException e) {
            // 아래에서 같은 문장으로 알린다
        }
        throw new IllegalStateException(text + ": date는 YYYY-MM-DD 또는 YYYY-MM-DDTHH:MM이다");
    }

    // [경도, 위도] 두 수이고 view 안이어야 한다
    private static void checkPoint(List<Double> point, BattleJson.View v, String where) {
        if (point == null || point.size() != 2 || point.get(0) == null || point.get(1) == null) {
            throw new IllegalStateException(where + ": 좌표는 [경도, 위도]다");
        }
        double lon = point.get(0);
        double lat = point.get(1);
        if (lon < v.west() || lon > v.east() || lat < v.south() || lat > v.north()) {
            throw new IllegalStateException(where + ": 좌표가 view 밖이다");
        }
    }

    // 사건이 있고, 전투이고, 키프레임 날짜가 사건 기간 안인지
    static void checkEvent(BattleJson b, EventInfo event) {
        if (event == null) {
            throw new IllegalStateException("사건이 없다: " + b.eventId());
        }
        if (!"battle".equals(event.kind())) {
            throw new IllegalStateException("전투(kind=battle) 사건에만 재생을 단다");
        }
        LocalDate earliest = event.startDate().minusDays(MAX_DAYS_BEFORE_START);
        for (int i = 0; i < b.keyframes().size(); i++) {
            BattleJson.Keyframe k = b.keyframes().get(i);
            LocalDate date = parseKeyDate(k.date()).toLocalDate();
            // 첫 날짜만 시작일보다 앞설 수 있다 (전투 직전 배치)
            LocalDate from = i == 0 ? earliest : event.startDate();
            if (date.isBefore(from) || date.isAfter(event.endDate())) {
                throw new IllegalStateException(k.date() + ": 사건 기간(" + event.startDate() + "~" + event.endDate() + ") 밖이다");
            }
        }
    }
}
