package io.github.crm08134gif.backend.battle;

import java.util.List;
import java.util.Map;

// data/battles/<사건 id>.json 하나 = 전투 재생 하나. 형식은 docs/battle-replay.md 2장
// 이 모양 그대로 화면에 보낸다 (status만 빼고, BattleCatalog.list 참고)
record BattleJson(
    String eventId,
    String status,
    View view,
    List<MapRef> maps,
    List<Unit> units,
    List<Keyframe> keyframes,
    // 근거 지도에 전선이 없어 스냅샷 전선이 맞지 않는 전투는 false. 빼면 true로 본다
    Boolean showFront,
    // 지도에 얹는 지명(근거 지도에 적힌 마을·산). 없으면 null
    List<Place> places
) {

    // 지명 하나. at은 [경도, 위도]
    record Place(String name, List<Double> at) {
    }

    // 재생할 때 지도에 맞출 범위 (경위도)
    record View(double west, double south, double east, double north) {
    }

    // 근거 지도 한 장. 화면 맨 아래 "근거 지도"에 그대로 보인다
    record MapRef(String title, String note) {
    }

    // 부대 하나. country는 국기 파일 이름(frontend/public/flags/<country>.svg), side는 un / communist
    record Unit(String id, String name, String country, String side) {
    }

    // 날짜 하나. front는 전투 지역의 전선 조각(서→동 [경도, 위도]), positions는 그날 있는 부대의 [경도, 위도],
    // arrows는 앞 날짜 다음 날부터 이 날짜까지의 공격·이동 화살표 (없으면 null)
    record Keyframe(
        String date,
        String caption,
        List<List<Double>> front,
        Map<String, List<Double>> positions,
        List<Arrow> arrows
    ) {
    }

    // 화살표 하나. side는 un / communist, path는 꼬리 → 머리 순서의 [경도, 위도]
    record Arrow(String side, List<List<Double>> path) {
    }
}
