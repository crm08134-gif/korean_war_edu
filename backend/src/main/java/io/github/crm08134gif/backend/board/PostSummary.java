package io.github.crm08134gif.backend.board;

import java.time.Instant;

// 목록의 글 한 줄. 가려진 글을 남이 보면 title이 null이다
public record PostSummary(
    long id,
    String targetId,
    String title,
    String author,
    boolean blinded,
    long likes,
    boolean liked,
    boolean mine,
    Instant createdAt
) {}
