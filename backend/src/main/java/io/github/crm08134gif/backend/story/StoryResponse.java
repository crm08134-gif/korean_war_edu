package io.github.crm08134gif.backend.story;

import com.fasterxml.jackson.annotation.JsonRawValue;

record StoryResponse(
    String id,
    String title,
    String summary,
    String photoFile,
    String photoCaption,
    // DB가 만든 JSON 글자를 다시 감싸지 않고 그대로 내보낸다
    @JsonRawValue String body,
    @JsonRawValue String sources,
    @JsonRawValue String photos,
    String status
) {
}
