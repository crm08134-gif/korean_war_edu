package io.github.crm08134gif.backend.board;

import java.time.Instant;

// 가려진 댓글을 남이 보면 body가 null이다
public record CommentView(
    long id,
    String body,
    String author,
    boolean blinded,
    long likes,
    boolean liked,
    boolean mine,
    Instant createdAt,
    Instant updatedAt
) {}
