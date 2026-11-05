package io.github.crm08134gif.backend.chat;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.crm08134gif.backend.TestcontainersConfiguration;
import io.github.crm08134gif.backend.auth.TokenService;
import io.github.crm08134gif.backend.member.Member;
import io.github.crm08134gif.backend.member.MemberRepository;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

// AI 질문이 로그인한 회원만, 올바른 요청만 받는지 확인한다.
// 테스트 설정(config/application.yaml)은 Gemini 키를 비워 두어 구글을 부르지 않는다
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChatSecurityTests {

    private static final String CSRF = "test-csrf";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    MemberRepository members;

    @Autowired
    TokenService tokenService;

    String token(String key) {
        Member member = members.save(new Member("kakao", "chat-" + key));
        member.changeNickname("질문" + key, Instant.now());
        members.flush();
        return tokenService.login(member, "test").accessToken();
    }

    // osan은 적재기가 data/events에서 넣는 실제 사건이다
    MockMvcRequestBuilder ask(String id, String question) {
        return mvc.post().uri("/api/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"event\",\"id\":\"" + id + "\",\"history\":[],\"question\":\"" + question + "\"}");
    }

    MockMvcRequestBuilder as(MockMvcRequestBuilder request, String token) {
        return request.cookie(new Cookie("access_token", token), new Cookie("XSRF-TOKEN", CSRF))
                .header("X-XSRF-TOKEN", CSRF);
    }

    @Test
    void 로그인하지_않으면_401() {
        assertThat(ask("osan", "왜 졌나요").cookie(new Cookie("XSRF-TOKEN", CSRF)).header("X-XSRF-TOKEN", CSRF))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void CSRF_없으면_403() {
        assertThat(ask("osan", "왜 졌나요").cookie(new Cookie("access_token", token("a"))))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void 빈_질문은_400() {
        assertThat(as(ask("osan", " "), token("b"))).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void 없는_카드는_404() {
        assertThat(as(ask("no-such-event", "왜 졌나요"), token("c"))).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void 키가_없으면_503() {
        assertThat(as(ask("osan", "왜 졌나요"), token("d"))).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
