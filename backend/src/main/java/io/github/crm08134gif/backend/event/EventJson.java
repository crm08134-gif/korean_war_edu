package io.github.crm08134gif.backend.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.LocalDate;
import java.util.List;

// data/events/<date>-<id>.json 한 파일
record EventJson(
    String id,
    String title,
    LocalDate date,
    String datePrecision,
    String placeName,
    Double lon,
    Double lat,
    String kind,
    String summary,
    List<Block> body,
    List<SourceRef> sources,
    String status
) {
    // 본문 블록. "type" 값을 보고 어느 record로 읽을지 정한다. 모르는 type이면 읽기에서 실패한다
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = Heading.class, name = "heading"),
        @JsonSubTypes.Type(value = Paragraph.class, name = "paragraph"),
        @JsonSubTypes.Type(value = Table.class, name = "table"),
    })
    sealed interface Block permits Heading, Paragraph, Table {
    }

    // 소제목 (배경, 경과, 결과, 의미)
    record Heading(String text) implements Block {
    }

    record Paragraph(String text) implements Block {
    }

    // 표. rows의 줄마다 칸 수가 columns와 같아야 한다. note는 표 아래 작은 글씨(출처 등)
    record Table(String caption, List<String> columns, List<List<String>> rows, String note) implements Block {
    }

    // 출처 목록 한 줄: sources.csv의 id와 장·쪽
    record SourceRef(String sourceId, String locator) {
    }
}
