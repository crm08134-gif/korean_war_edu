package io.github.crm08134gif.backend.chat;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 카드에 대한 AI 질문. 로그인한 회원만 (SecurityConfig의 "/api/**" authenticated가 막는다)
@RestController
@RequestMapping("/api/chat")
class ChatController {

    private final ChatService service;

    ChatController(ChatService service) {
        this.service = service;
    }

    // 누가 묻는지는 서명된 토큰의 회원 번호로만 정한다 (CLAUDE.md 7장 원칙)
    @PostMapping
    ChatResponse ask(@RequestBody ChatRequest request, @AuthenticationPrincipal Jwt jwt) {
        return service.ask(Long.parseLong(jwt.getSubject()), request);
    }
}
