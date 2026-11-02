package io.github.crm08134gif.backend.board;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 글 고치기. 게시판과 대상은 바꿀 수 없으므로 아예 받지 않는다
record PostEditRequest(
    @NotBlank(message = "제목을 써 주세요")
    @Size(max = 100, message = "제목은 100자까지 쓸 수 있습니다")
    String title,

    @NotBlank(message = "본문을 써 주세요")
    @Size(max = 5000, message = "본문은 5,000자까지 쓸 수 있습니다")
    String body) {
}
