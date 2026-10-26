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
    String category,
    String hook,
    String body,
    String confidenceTier,
    String status,
    @JsonRawValue String claims
) {
}
