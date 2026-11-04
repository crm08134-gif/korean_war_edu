package io.github.crm08134gif.backend.board;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

// 글쓰기 제재 기간(일). 0이면 푼다
record BanRequest(
    @Min(value = 0, message = "제재 기간은 0~365일입니다")
    @Max(value = 365, message = "제재 기간은 0~365일입니다")
    int days) {
}
