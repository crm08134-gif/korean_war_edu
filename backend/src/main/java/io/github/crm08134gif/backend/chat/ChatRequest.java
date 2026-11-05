package io.github.crm08134gif.backend.chat;

import java.util.List;

// 프론트가 보내는 질문.
// type: "event"(사건 카드) 또는 "story"(이야기 카드), id: 그 카드의 id
// history: 이 시트에서 앞서 주고받은 말 (화면을 닫으면 사라진다. 서버는 저장하지 않는다)
record ChatRequest(String type, String id, List<ChatTurn> history, String question) {
}
