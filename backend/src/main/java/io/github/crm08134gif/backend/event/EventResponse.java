package io.github.crm08134gif.backend.event;

import com.fasterxml.jackson.annotation.JsonRawValue;
import java.time.LocalDate;

record EventResponse (
    String id,
    String title,
    LocalDate date,
    String datePrecision,
    String placeName,
    double lon,
    double lat,
    String kind,
    String summary,
    // DB가 만든 JSON 글자를 다시 감싸지 않고 그대로 내보낸다
    @JsonRawValue String body,
    @JsonRawValue String sources,
    String status
) {
}
