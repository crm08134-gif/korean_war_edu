package io.github.crm08134gif.backend.battle;

import java.util.List;

// 화면에 보내는 전투 재생 하나. 검수 상태(status)는 필터에만 쓰고 내보내지 않는다
record BattleResponse(
    String eventId,
    BattleJson.View view,
    List<BattleJson.MapRef> maps,
    List<BattleJson.Unit> units,
    List<BattleJson.Keyframe> keyframes
) {
}
