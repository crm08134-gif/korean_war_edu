package io.github.crm08134gif.backend.vote;

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

// 인물 투표가 로그인한 회원만, 한 사람 한 표로 되는지 확인한다
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class VoteSecurityTests {

    // 적재기가 data/events에서 넣는 실제 사건과 그 사건의 인물
    private static final String EVENT = "osan";
    private static final String PERSON = "charles-b-smith";
    private static final String CSRF = "test-csrf";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    MemberRepository members;

    @Autowired
    TokenService tokenService;

    String token(String key, boolean withNickname) {
        Member member = members.save(new Member("kakao", "vote-" + key));
        if (withNickname) {
            member.changeNickname("투표" + key, Instant.now());
        }
        members.flush();
        return tokenService.login(member, "test").accessToken();
    }

    MockMvcRequestBuilder put(int value) {
        return mvc.put().uri("/api/votes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"" + EVENT + "\",\"personId\":\"" + PERSON + "\",\"value\":" + value + "}");
    }

    MockMvcRequestBuilder as(MockMvcRequestBuilder request, String token) {
        return request.cookie(new Cookie("access_token", token), new Cookie("XSRF-TOKEN", CSRF))
                .header("X-XSRF-TOKEN", CSRF);
    }

    // JSON 배열에서 그 인물의 줄만 고른다. 결과는 배열로 나온다
    String path(String field) {
        return "$[?(@.personId == '" + PERSON + "')]." + field;
    }

    @Test
    void 로그인하지_않아도_숫자는_보인다() {
        assertThat(mvc.get().uri("/api/votes?eventId=" + EVENT)).hasStatusOk();
    }

    @Test
    void 로그인하지_않고_누르면_401() {
        assertThat(put(1).cookie(new Cookie("XSRF-TOKEN", CSRF)).header("X-XSRF-TOKEN", CSRF))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void CSRF_없이_누르면_403() {
        assertThat(put(1).cookie(new Cookie("access_token", token("a", true))))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void 닉네임이_없으면_누를_수_없다() {
        assertThat(as(put(1), token("b", false))).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void 한_회원은_한_표이고_다시_누르면_바뀌거나_취소된다() {
        String token = token("c", true);
        assertThat(as(put(1), token)).hasStatusOk();
        assertThat(as(put(1), token)).hasStatusOk()
                .bodyJson().extractingPath(path("up")).asArray().containsExactly(1);
        assertThat(as(put(-1), token)).hasStatusOk()
                .bodyJson().extractingPath(path("mine")).asArray().containsExactly(-1);
        assertThat(as(put(0), token)).hasStatusOk()
                .bodyJson().extractingPath(path("mine")).asArray().containsExactly(0);
    }

    @Test
    void 남의_표는_내_표로_보이지_않는다() {
        assertThat(as(put(1), token("d", true))).hasStatusOk();
        assertThat(as(mvc.get().uri("/api/votes?eventId=" + EVENT), token("e", true))).hasStatusOk()
                .bodyJson().extractingPath(path("mine")).asArray().containsExactly(0);
        assertThat(mvc.get().uri("/api/votes?eventId=" + EVENT)).hasStatusOk()
                .bodyJson().extractingPath(path("mine")).asArray().containsExactly(0);
    }
}
