package io.github.crm08134gif.backend.board;

import java.time.Instant;
import java.util.List;

// 글 본문 화면. 가려진 글을 남이 보면 title, body가 null이다
public record PostDetail(
    long id,
    BoardType board,
    String targetId,
    String title,
    String body,
    String author,
    boolean blinded,
    long likes,
    boolean liked,
    boolean mine,
    Instant createdAt,
    Instant updatedAt,
    List<CommentView> comments
) {}
