package io.github.crm08134gif.backend.board;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 새 글. 길이는 V12의 CHECK와 같게 (서버가 먼저 막고, DB가 마지막으로 막는다)
// 작성자 칸이 없다: 작성자는 늘 토큰의 회원이다
record PostRequest(
    @NotNull
    BoardType board,

    @NotBlank
    @Size(max = 64)
    String targetId,

    @NotBlank(message = "제목을 써 주세요")
    @Size(max = 100, message = "제목은 100자까지 쓸 수 있습니다")
    String title,

    @NotBlank(message = "본문을 써 주세요")
    @Size(max = 5000, message = "본문은 5,000자까지 쓸 수 있습니다")
    String body) {
}
