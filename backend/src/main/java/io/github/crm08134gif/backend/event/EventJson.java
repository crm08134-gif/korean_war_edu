package io.github.crm08134gif.backend.event;

import java.time.LocalDate;
import java.util.List;

record EventJson(
    String id,
    String title,
    LocalDate date,
    String datePrecision,
    String placeName,
    Double lon,
    Double lat,
    String category,
    String hook,
    String body,
    String confidenceTier,
    String status,
    List<Claim> claims
) {
    record Claim(String text, List<Evidence> evidence) {
    }

    record Evidence(String sourceId, String locator, String quote) {
    }
}
