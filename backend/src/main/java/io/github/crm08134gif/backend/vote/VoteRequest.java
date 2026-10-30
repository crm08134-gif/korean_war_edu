package io.github.crm08134gif.backend.vote;

// PUT /api/votes 본문. value: 1 좋아요, -1 싫어요, 0 취소
record VoteRequest(String eventId, String personId, int value) {
}
