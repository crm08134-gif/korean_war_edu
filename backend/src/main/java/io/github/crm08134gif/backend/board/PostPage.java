package io.github.crm08134gif.backend.board;

import java.util.List;

// 목록 한 페이지. nextCursor가 null이면 마지막 페이지다
public record PostPage(List<PostSummary> posts, Long nextCursor) {}
