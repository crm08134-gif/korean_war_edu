package io.github.crm08134gif.backend.frontline;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

record SnapshotCsvRow (
    @JsonProperty("snapshot_date") LocalDate snapshotDate,
    @JsonProperty("title") String title,
    @JsonProperty("source_map") String sourceMap,
    @JsonProperty("note") String note
){

}

