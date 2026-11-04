package io.github.crm08134gif.backend.board;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.crm08134gif.backend.TestcontainersConfiguration;
import io.github.crm08134gif.backend.auth.IssuedTokens;
import io.github.crm08134gif.backend.auth.TokenService;
import io.github.crm08134gif.backend.member.Member;
import io.github.crm08134gif.backend.member.MemberRepository;
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

// 게시판 쓰기 규칙이 실제로 막는지 확인한다
// @Transactional: 테스트마다 끝나면 되돌려서 서로 영향을 주지 않는다 (작성 횟수 제한 때문에 특히 중요)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BoardSecurityTests {

    // 적재기가 data/events에서 넣는 실제 사건
    private static final String EVENT = "osan";
    private static final String CSRF = "test-csrf";
    // AuthCookies.ACCESS와 같은 이름 (그 클래스는 auth 패키지 밖에서 안 보인다)
    private static final String ACCESS = "access_token";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    MemberRepository members;

    @Autowired
    TokenService tokenService;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    EntityManager em;

    // 닉네임까지 정한 회원을 만들고 액세스 토큰을 돌려준다
    record Writer(long id, String token) {}

    Writer writer(String key) {
        Member member = members.save(new Member("kakao", "board-" + key));
        member.changeNickname("기록" + key, Instant.now());
        members.flush();
        IssuedTokens tokens = tokenService.login(member, "test");
        return new Writer(member.getId(), tokens.accessToken());
    }

    // SQL로 바꾼 값을 서비스가 보게 한다. 안 하면 JPA가 1차 캐시에 들고 있던 옛 값을 쓴다
    void sql(String statement, long id) {
        jdbc.sql(statement).param("id", id).update();
        em.clear();
    }

    // 프론트처럼 CSRF 쿠키와 같은 값을 머리글에 넣는다
    MockMvcRequestBuilder as(MockMvcRequestBuilder request, Writer w) {
        return request.cookie(new Cookie(ACCESS, w.token()), new Cookie("XSRF-TOKEN", CSRF))
                .header("X-XSRF-TOKEN", CSRF);
    }

    MockMvcRequestBuilder json(MockMvcRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    String newPost(String title) {
        return "{\"board\":\"EVENT\",\"targetId\":\"" + EVENT + "\",\"title\":\"" + title + "\",\"body\":\"본문\"}";
    }

    // 가장 최근에 만든 행 번호 (테스트마다 되돌리므로 방금 만든 것)
    long lastId(String table) {
        return jdbc.sql("SELECT max(id) FROM " + table).query(Long.class).single();
    }

    // 글을 하나 쓰고 번호를 돌려준다
    long post(Writer w) {
        assertThat(as(json(mvc.post().uri("/api/posts"), newPost("제목")), w)).hasStatus(HttpStatus.CREATED);
        return lastId("post");
    }

    // ---------- 로그인·CSRF ----------

    @Test
    void 로그인하지_않고_글을_쓰면_401() {
        assertThat(json(mvc.post().uri("/api/posts"), newPost("제목"))
                .cookie(new Cookie("XSRF-TOKEN", CSRF)).header("X-XSRF-TOKEN", CSRF))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void CSRF_없이_쓰면_403() {
        Writer w = writer("a");
        assertThat(json(mvc.post().uri("/api/posts"), newPost("제목"))
                .cookie(new Cookie(ACCESS, w.token())))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void 글_목록과_본문은_로그인_없이_읽힌다() {
        long id = post(writer("b"));
        assertThat(mvc.get().uri("/api/posts?board=EVENT&targetId=" + EVENT)).hasStatusOk();
        assertThat(mvc.get().uri("/api/posts/" + id)).hasStatusOk()
                .bodyJson().extractingPath("$.mine").isEqualTo(false);
    }

    // ---------- 본인 확인 ----------

    @Test
    void 남의_글은_고치거나_지울_수_없다() {
        long id = post(writer("c"));
        Writer other = writer("d");
        assertThat(as(json(mvc.patch().uri("/api/posts/" + id), "{\"title\":\"바꿈\",\"body\":\"바꿈\"}"), other))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(as(mvc.delete().uri("/api/posts/" + id), other)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void 남의_댓글은_지울_수_없다() {
        Writer author = writer("e");
        long postId = post(author);
        assertThat(as(json(mvc.post().uri("/api/posts/" + postId + "/comments"), "{\"body\":\"댓글\"}"), author))
                .hasStatus(HttpStatus.CREATED);
        long commentId = lastId("comment");
        assertThat(as(mvc.delete().uri("/api/comments/" + commentId), writer("f"))).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void 지운_글은_404() {
        Writer w = writer("g");
        long id = post(w);
        assertThat(as(mvc.delete().uri("/api/posts/" + id), w)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/posts/" + id)).hasStatus(HttpStatus.NOT_FOUND);
    }

    // ---------- 회원 상태 (토큰은 살아 있어도 DB 상태로 막는다) ----------

    @Test
    void 제재_중이면_쓸_수_없다() {
        Writer w = writer("h");
        sql("UPDATE member SET write_banned_until = now() + interval '1 day' WHERE id = :id", w.id());
        assertThat(as(json(mvc.post().uri("/api/posts"), newPost("제목")), w)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void 탈퇴한_회원의_토큰으로는_쓸_수_없다() {
        Writer w = writer("i");
        sql("UPDATE member SET withdrawn_at = now() WHERE id = :id", w.id());
        assertThat(as(json(mvc.post().uri("/api/posts"), newPost("제목")), w)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void 닉네임이_없으면_쓸_수_없다() {
        Member member = members.save(new Member("kakao", "board-nonick"));
        Writer w = new Writer(member.getId(), tokenService.login(member, "test").accessToken());
        assertThat(as(json(mvc.post().uri("/api/posts"), newPost("제목")), w)).hasStatus(HttpStatus.FORBIDDEN);
    }

    // ---------- 입력 검사 ----------

    @Test
    void 작성_횟수를_넘으면_429() {
        Writer w = writer("j");
        post(w);
        assertThat(as(json(mvc.post().uri("/api/posts"), newPost("두번째")), w)).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void 없는_사건에는_쓸_수_없다() {
        Writer w = writer("k");
        String body = "{\"board\":\"EVENT\",\"targetId\":\"no-such-event\",\"title\":\"t\",\"body\":\"b\"}";
        assertThat(as(json(mvc.post().uri("/api/posts"), body), w)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void 공백뿐인_제목과_너무_긴_제목은_400() {
        Writer w = writer("l");
        assertThat(as(json(mvc.post().uri("/api/posts"), newPost("   ")), w)).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(as(json(mvc.post().uri("/api/posts"), newPost("가".repeat(101))), w)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void 요청에_작성자를_넣어도_토큰의_회원으로_쓴다() {
        Writer w = writer("m");
        Writer victim = writer("n");
        String body = "{\"board\":\"EVENT\",\"targetId\":\"" + EVENT + "\",\"title\":\"t\",\"body\":\"b\",\"authorId\":"
                + victim.id() + "}";
        // 모르는 칸(authorId)은 무시되거나 400이다. 어느 쪽이든 남의 이름으로 글이 생기면 안 된다
        int status = as(json(mvc.post().uri("/api/posts"), body), w).exchange().getResponse().getStatus();
        assertThat(status).isIn(201, 400);
        Long victimPosts = jdbc.sql("SELECT count(*) FROM post WHERE author_id = :id")
                .param("id", victim.id()).query(Long.class).single();
        assertThat(victimPosts).isZero();
    }

    // ---------- 가리기 ----------

    @Test
    void 가려진_글은_남에게_내용이_안_보이고_작성자에게는_보인다() {
        Writer author = writer("o");
        long id = post(author);
        sql("UPDATE post SET status = 'BLINDED' WHERE id = :id", id);

        assertThat(mvc.get().uri("/api/posts/" + id)).hasStatusOk()
                .bodyJson().extractingPath("$.body").isNull();
        assertThat(mvc.get().uri("/api/posts?board=EVENT&targetId=" + EVENT)).hasStatusOk()
                .bodyJson().extractingPath("$.posts[0].title").isNull();
        assertThat(as(mvc.get().uri("/api/posts/" + id), author)).hasStatusOk()
                .bodyJson().extractingPath("$.body").isEqualTo("본문");
    }

    @Test
    void 가려진_글은_고칠_수_없다() {
        Writer author = writer("p");
        long id = post(author);
        sql("UPDATE post SET status = 'BLINDED' WHERE id = :id", id);
        assertThat(as(json(mvc.patch().uri("/api/posts/" + id), "{\"title\":\"바꿈\",\"body\":\"바꿈\"}"), author))
                .hasStatus(HttpStatus.CONFLICT);
    }

    // ---------- 공감 ----------

    @Test
    void 공감은_두_번_눌러도_하나다() {
        long id = post(writer("q"));
        Writer fan = writer("r");
        assertThat(as(mvc.put().uri("/api/posts/" + id + "/like"), fan)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(as(mvc.put().uri("/api/posts/" + id + "/like"), fan)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(as(mvc.get().uri("/api/posts/" + id), fan)).hasStatusOk()
                .bodyJson().extractingPath("$.likes").isEqualTo(1);

        assertThat(as(mvc.delete().uri("/api/posts/" + id + "/like"), fan)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/posts/" + id)).hasStatusOk()
                .bodyJson().extractingPath("$.likes").isEqualTo(0);
    }
}
