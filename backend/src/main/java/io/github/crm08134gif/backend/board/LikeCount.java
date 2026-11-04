package io.github.crm08134gif.backend.board;

// 글(또는 댓글) 하나의 공감 수와 "내가 눌렀나"
// 칸 이름(id, likes, mine)이 SQL의 별칭과 같아야 JdbcClient가 자동으로 채운다
public record LikeCount(long id, long likes, boolean mine) {}
