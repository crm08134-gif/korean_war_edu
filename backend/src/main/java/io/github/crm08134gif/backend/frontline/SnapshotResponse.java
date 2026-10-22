package io.github.crm08134gif.backend.frontline;

import com.fasterxml.jackson.annotation.JsonRawValue;
import java.time.LocalDate;

record SnapshotResponse(
    LocalDate snapshotDate,
    String sourceMap,
    String note,
    @JsonRawValue String frontLine,
    @JsonRawValue String extraAreas
) {
}
