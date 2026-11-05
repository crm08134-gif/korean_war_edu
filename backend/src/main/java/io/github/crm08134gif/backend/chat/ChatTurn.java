package io.github.crm08134gif.backend.chat;

// 앞서 주고받은 말 하나. role은 "user"(회원) 또는 "model"(AI). Gemini가 쓰는 이름 그대로다
record ChatTurn(String role, String text) {
}
