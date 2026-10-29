package io.github.crm08134gif.backend.story;

import io.github.crm08134gif.backend.event.EventJson;
import java.util.List;

// data/stories/<id>.json 한 파일. 사건 파일에서 날짜·장소·종류를 뺀 모양이다
record StoryJson(
    String id,
    String title,
    String summary,
    EventJson.Photo photo,
    List<EventJson.Block> body,
    List<EventJson.SourceRef> sources,
    String status
) {
}
