package io.github.crm08134gif.backend.battle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.crm08134gif.backend.TestcontainersConfiguration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

// 전투 재생 목록이 공개로 읽히고, 잘못된 파일은 앱이 뜨기 전에 막히는지 확인한다.
// 앱이 켜졌다는 것 자체가 data/battles의 실제 파일이 모두 검사를 통과했다는 뜻이다
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class BattleTests {

    @Autowired
    MockMvcTester mvc;

    // 검사를 통과하는 가장 작은 전투 재생. 시험마다 한 칸씩 바꿔 본다
    static BattleJson valid(List<BattleJson.Unit> units, List<BattleJson.Keyframe> keyframes) {
        return new BattleJson("dabudong", "draft",
                new BattleJson.View(128.0, 35.7, 129.0, 36.6),
                List.of(new BattleJson.MapRef("CMH Pub 20-2, Map 0", null)),
                units, keyframes, null);
    }

    static final List<BattleJson.Unit> UNITS = List.of(
            new BattleJson.Unit("rok-1", "국군 1사단", "kr", "un"),
            new BattleJson.Unit("nk-13", "북한 13사단", "kp", "communist"));

    static BattleJson.Keyframe day(String date, Map<String, List<Double>> positions) {
        return new BattleJson.Keyframe(date, "설명", null, positions, null);
    }

    static final List<BattleJson.Keyframe> DAYS = List.of(
            day("1950-08-13", Map.of("rok-1", List.of(128.5, 36.1), "nk-13", List.of(128.4, 36.3))),
            day("1950-08-18", Map.of("rok-1", List.of(128.5, 36.0))));

    static final BattleCatalog.EventInfo DABUDONG =
            new BattleCatalog.EventInfo("battle", LocalDate.parse("1950-08-13"), LocalDate.parse("1950-08-30"));

    @Test
    void 로그인_없이_읽는다() {
        assertThat(mvc.get().uri("/api/battles")).hasStatus(HttpStatus.OK);
    }

    @Test
    void 올바른_파일은_통과한다() {
        BattleCatalog.check(valid(UNITS, DAYS), "dabudong.json");
        BattleCatalog.checkEvent(valid(UNITS, DAYS), DABUDONG);
    }

    @Test
    void 파일_이름과_사건_id가_다르면_막는다() {
        assertThatThrownBy(() -> BattleCatalog.check(valid(UNITS, DAYS), "other.json")).hasMessageContaining("파일 이름");
    }

    @Test
    void units에_없는_부대는_막는다() {
        List<BattleJson.Keyframe> days = List.of(
                day("1950-08-13", Map.of("rok-1", List.of(128.5, 36.1))),
                day("1950-08-18", Map.of("us-27", List.of(128.5, 36.0))));
        assertThatThrownBy(() -> BattleCatalog.check(valid(UNITS, days), "dabudong.json")).hasMessageContaining("units에 없는");
    }

    @Test
    void view_밖의_좌표는_막는다() {
        List<BattleJson.Keyframe> days = List.of(
                day("1950-08-13", Map.of("rok-1", List.of(127.0, 36.1))),
                day("1950-08-18", Map.of("rok-1", List.of(128.5, 36.0))));
        assertThatThrownBy(() -> BattleCatalog.check(valid(UNITS, days), "dabudong.json")).hasMessageContaining("view 밖");
    }

    @Test
    void 날짜가_거꾸로면_막는다() {
        List<BattleJson.Keyframe> days = List.of(
                day("1950-08-18", Map.of("rok-1", List.of(128.5, 36.1))),
                day("1950-08-13", Map.of("rok-1", List.of(128.5, 36.0))));
        assertThatThrownBy(() -> BattleCatalog.check(valid(UNITS, days), "dabudong.json")).hasMessageContaining("앞 날짜보다");
    }

    @Test
    void 화살표는_통과하고_view_밖이나_첫_날짜면_막는다() {
        BattleJson.Arrow ok = new BattleJson.Arrow("communist", List.of(List.of(128.4, 36.3), List.of(128.5, 36.15)));
        BattleJson.Arrow outside = new BattleJson.Arrow("un", List.of(List.of(128.5, 36.0), List.of(130.0, 36.1)));
        Map<String, List<Double>> at = Map.of("rok-1", List.of(128.5, 36.1));

        List<BattleJson.Keyframe> good = List.of(
                day("1950-08-13", at),
                new BattleJson.Keyframe("1950-08-18", "설명", null, at, List.of(ok)));
        BattleCatalog.check(valid(UNITS, good), "dabudong.json");

        List<BattleJson.Keyframe> out = List.of(
                day("1950-08-13", at),
                new BattleJson.Keyframe("1950-08-18", "설명", null, at, List.of(outside)));
        assertThatThrownBy(() -> BattleCatalog.check(valid(UNITS, out), "dabudong.json")).hasMessageContaining("view 밖");

        List<BattleJson.Keyframe> onFirst = List.of(
                new BattleJson.Keyframe("1950-08-13", "설명", null, at, List.of(ok)),
                day("1950-08-18", at));
        assertThatThrownBy(() -> BattleCatalog.check(valid(UNITS, onFirst), "dabudong.json")).hasMessageContaining("첫 날짜");
    }

    @Test
    void 사건_기간_밖의_날짜는_막는다() {
        List<BattleJson.Keyframe> days = List.of(
                day("1950-08-13", Map.of("rok-1", List.of(128.5, 36.1))),
                day("1950-09-02", Map.of("rok-1", List.of(128.5, 36.0))));
        assertThatThrownBy(() -> BattleCatalog.checkEvent(valid(UNITS, days), DABUDONG)).hasMessageContaining("사건 기간");
    }

    @Test
    void 첫_날짜만_사건_시작보다_앞설_수_있다() {
        Map<String, List<Double>> at = Map.of("rok-1", List.of(128.5, 36.1));
        List<BattleJson.Keyframe> before = List.of(day("1950-08-05", at), day("1950-08-24", at));
        BattleCatalog.checkEvent(valid(UNITS, before), DABUDONG);

        List<BattleJson.Keyframe> tooEarly = List.of(day("1950-07-20", at), day("1950-08-24", at));
        assertThatThrownBy(() -> BattleCatalog.checkEvent(valid(UNITS, tooEarly), DABUDONG)).hasMessageContaining("사건 기간");

        List<BattleJson.Keyframe> secondBefore = List.of(day("1950-08-05", at), day("1950-08-10", at));
        assertThatThrownBy(() -> BattleCatalog.checkEvent(valid(UNITS, secondBefore), DABUDONG)).hasMessageContaining("사건 기간");
    }

    @Test
    void 하루짜리_전투는_시각으로_나눈다() {
        Map<String, List<Double>> at = Map.of("rok-1", List.of(128.5, 36.1));
        List<BattleJson.Keyframe> hours = List.of(day("1950-08-13T07:45", at), day("1950-08-13T14:30", at));
        BattleCatalog.check(valid(UNITS, hours), "dabudong.json");

        List<BattleJson.Keyframe> backwards = List.of(day("1950-08-13T14:30", at), day("1950-08-13T07:45", at));
        assertThatThrownBy(() -> BattleCatalog.check(valid(UNITS, backwards), "dabudong.json")).hasMessageContaining("앞 날짜보다");

        List<BattleJson.Keyframe> bad = List.of(day("1950-08-13 07:45", at), day("1950-08-14", at));
        assertThatThrownBy(() -> BattleCatalog.check(valid(UNITS, bad), "dabudong.json")).hasMessageContaining("YYYY-MM-DD");
    }

    @Test
    void 전투가_아닌_사건에는_달지_않는다() {
        BattleCatalog.EventInfo capture =
                new BattleCatalog.EventInfo("capture", LocalDate.parse("1950-08-13"), LocalDate.parse("1950-08-30"));
        assertThatThrownBy(() -> BattleCatalog.checkEvent(valid(UNITS, DAYS), capture)).hasMessageContaining("전투");
    }
}
