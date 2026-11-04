package io.github.crm08134gif.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.crm08134gif.backend.TestcontainersConfiguration;
import io.github.crm08134gif.backend.member.Member;
import io.github.crm08134gif.backend.member.MemberRepository;
import io.github.crm08134gif.backend.member.MemberService;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

// 탈퇴하면 사람을 알아볼 수 있는 정보가 지워지고, 로그인이 바로 끊기는지 확인한다
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WithdrawTests {

    private static final String CSRF = "test-csrf";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    MemberRepository members;

    @Autowired
    MemberService memberService;

    @Autowired
    TokenService tokenService;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    EntityManager em;

    record User(long id, IssuedTokens tokens) {}

    User user(String key) {
        Member member = members.save(new Member("kakao", "withdraw-" + key));
        member.changeNickname("탈퇴시험" + key, Instant.now());
        members.flush();
        return new User(member.getId(), tokenService.login(member, "test"));
    }

    MockMvcRequestBuilder as(MockMvcRequestBuilder request, User u) {
        return request.cookie(new Cookie(AuthCookies.ACCESS, u.tokens().accessToken()), new Cookie("XSRF-TOKEN", CSRF))
                .header("X-XSRF-TOKEN", CSRF);
    }

    MockMvcRequestBuilder withdraw(User u) {
        return as(mvc.delete().uri("/api/me"), u);
    }

    // 테스트 전체가 한 트랜잭션이라 서비스가 바꾼 값을 SQL로 보기 전에 flush한다
    <T> T column(String sql, long id, Class<T> type) {
        em.flush();
        em.clear();
        return jdbc.sql(sql).param("id", id).query(type).optional().orElse(null);
    }

    @Test
    void 탈퇴하면_회원_번호와_닉네임이_지워진다() {
        User u = user("a");
        assertThat(withdraw(u)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(column("SELECT provider_user_id FROM member WHERE id = :id", u.id(), String.class)).isNull();
        assertThat(column("SELECT nickname FROM member WHERE id = :id", u.id(), String.class)).isNull();
        assertThat(column("SELECT withdrawn_at IS NOT NULL FROM member WHERE id = :id", u.id(), Boolean.class)).isTrue();
    }

    @Test
    void 탈퇴하면_로그인_기록이_모두_지워지고_갱신이_안_된다() {
        User u = user("b");
        tokenService.login(members.findById(u.id()).orElseThrow(), "다른 기기");
        assertThat(withdraw(u)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(column("SELECT count(*) FROM refresh_token WHERE member_id = :id", u.id(), Long.class)).isZero();
        assertThat(tokenService.refresh(u.tokens().refreshToken(), "test")).isEmpty();
    }

    @Test
    void 탈퇴한_회원의_남은_토큰으로는_쓸_수_없다() {
        User u = user("c");
        assertThat(withdraw(u)).hasStatus(HttpStatus.NO_CONTENT);
        em.flush();
        em.clear();
        String body = "{\"board\":\"EVENT\",\"targetId\":\"osan\",\"title\":\"t\",\"body\":\"b\"}";
        assertThat(as(mvc.post().uri("/api/posts").contentType(MediaType.APPLICATION_JSON).content(body), u))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        // 두 번 탈퇴할 수도 없다
        assertThat(withdraw(u)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void 탈퇴한_회원의_글은_탈퇴한_사용자로_보인다() {
        User u = user("d");
        String body = "{\"board\":\"EVENT\",\"targetId\":\"osan\",\"title\":\"제목\",\"body\":\"본문\"}";
        assertThat(as(mvc.post().uri("/api/posts").contentType(MediaType.APPLICATION_JSON).content(body), u))
                .hasStatus(HttpStatus.CREATED);
        long postId = jdbc.sql("SELECT max(id) FROM post").query(Long.class).single();
        assertThat(withdraw(u)).hasStatus(HttpStatus.NO_CONTENT);
        em.flush();
        em.clear();
        assertThat(mvc.get().uri("/api/posts/" + postId)).hasStatusOk()
                .bodyJson().extractingPath("$.author").isEqualTo("탈퇴한 사용자");
    }

    @Test
    void 같은_계정으로_다시_로그인하면_새_회원이다() {
        User u = user("e");
        assertThat(withdraw(u)).hasStatus(HttpStatus.NO_CONTENT);
        em.flush();
        em.clear();
        Member again = memberService.findOrCreate("kakao", "withdraw-e");
        assertThat(again.getId()).isNotEqualTo(u.id());
        assertThat(again.needsNickname()).isTrue();
    }

    @Test
    void CSRF_없이는_탈퇴할_수_없다() {
        User u = user("f");
        assertThat(mvc.delete().uri("/api/me").cookie(new Cookie(AuthCookies.ACCESS, u.tokens().accessToken())))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(column("SELECT nickname FROM member WHERE id = :id", u.id(), String.class)).isEqualTo("탈퇴시험f");
    }

    @Test
    void 로그인하지_않으면_401() {
        assertThat(mvc.delete().uri("/api/me").cookie(new Cookie("XSRF-TOKEN", CSRF)).header("X-XSRF-TOKEN", CSRF))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
