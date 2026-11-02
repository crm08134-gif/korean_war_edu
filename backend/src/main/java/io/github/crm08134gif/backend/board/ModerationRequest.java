package io.github.crm08134gif.backend.board;

import jakarta.validation.constraints.NotNull;

// 관리자 처리: VISIBLE(되살리기) 또는 BLINDED(가리기). DELETED는 서비스가 거부한다
record ModerationRequest(@NotNull PostStatus status) {
}
