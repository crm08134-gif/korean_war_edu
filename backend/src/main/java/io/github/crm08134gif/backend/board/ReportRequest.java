package io.github.crm08134gif.backend.board;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 신고하기. 누가 신고했는지는 토큰으로만 정한다
record ReportRequest(
    @NotNull(message = "신고 이유를 골라 주세요")
    ReportReason reason,

    @Size(max = 500, message = "이유는 500자까지 쓸 수 있습니다")
    String detail) {
}
