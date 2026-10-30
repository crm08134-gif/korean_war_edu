package io.github.crm08134gif.backend.vote;

// 인물 한 명의 좋아요·싫어요 수와 이 기기가 누른 표(1, -1, 누르지 않았으면 0)
record VoteCount(String personId, long up, long down, int mine) {
}
