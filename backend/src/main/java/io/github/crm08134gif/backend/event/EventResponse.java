package io.github.crm08134gif.backend.event;

import com.fasterxml.jackson.annotation.JsonRawValue;
import java.time.LocalDate;

record EventResponse (
    String id,
    String title,
    LocalDate date,
    LocalDate endDate,
    String datePrecision,
    String placeName,
    Double lon, // region이 있는 사건은 좌표가 없다(null)
    Double lat,
    String region,
    String kind,
    String summary,
    String photoFile,
    String photoCaption,
    // DB가 만든 JSON 글자를 다시 감싸지 않고 그대로 내보낸다
    @JsonRawValue String body,
    @JsonRawValue String sources,
    @JsonRawValue String photos,
    @JsonRawValue String people,
    String status
) {
}
