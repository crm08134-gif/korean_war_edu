package io.github.crm08134gif.backend.board;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 댓글 쓰기·고치기에 함께 쓴다
record CommentRequest(
    @NotBlank(message = "댓글을 써 주세요")
    @Size(max = 1000, message = "댓글은 1,000자까지 쓸 수 있습니다")
    String body) {
}
