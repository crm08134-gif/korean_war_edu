package io.github.crm08134gif.backend.board;

import java.time.OffsetDateTime;

// 관리자 큐 한 줄. 칸 이름은 SQL 별칭(snake_case)과 이어진다 (post_id → postId)
// 시각은 OffsetDateTime: PostgreSQL 드라이버가 TIMESTAMPTZ를 바로 이 타입으로 준다
// author는 탈퇴했으면 null, title은 댓글이면 null
public record QueueItem(
    String targetType,
    long targetId,
    long postId,
    String status,
    String title,
    String body,
    long authorId,
    String author,
    OffsetDateTime authorBannedUntil,
    long reports,
    String reasons,
    String details,
    OffsetDateTime lastReportedAt
) {}
