package io.github.crm08134gif.backend.board;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.crm08134gif.backend.TestcontainersConfiguration;
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

// 신고 2명 기준과 관리자 권한이 실제로 지켜지는지 확인한다
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReportSecurityTests {

    private static final String EVENT = "osan";
    private static final String CSRF = "test-csrf";
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

    record User(long id, String token) {}

    User user(String key) {
        Member member = members.save(new Member("kakao", "report-" + key));
        member.changeNickname("신고" + key, Instant.now());
        members.flush();
        return new User(member.getId(), tokenService.login(member, "test").accessToken());
    }

    // DB에서 ADMIN으로 바꾼 뒤 로그인한다 (토큰의 role도 ADMIN이 된다)
    User admin(String key) {
        User u = user(key);
        sql("UPDATE member SET role = 'ADMIN' WHERE id = :id", u.id());
        Member member = members.findById(u.id()).orElseThrow();
        return new User(u.id(), tokenService.login(member, "test").accessToken());
    }

    // 테스트 전체가 한 트랜잭션이라, 서비스가 엔티티를 바꿔도 UPDATE는 아직 DB에 안 나갔을 수 있다.
    // SQL로 읽거나 쓰기 전에 flush로 내보내고, 쓴 뒤에는 clear로 JPA가 들고 있던 옛 값을 버린다
    void sql(String statement, long id) {
        em.flush();
        jdbc.sql(statement).param("id", id).update();
        em.clear();
    }

    String status(long postId) {
        em.flush();
        em.clear();
        return jdbc.sql("SELECT status FROM post WHERE id = :id").param("id", postId).query(String.class).single();
    }

    MockMvcRequestBuilder as(MockMvcRequestBuilder request, User u) {
        return request.cookie(new Cookie(ACCESS, u.token()), new Cookie("XSRF-TOKEN", CSRF)).header("X-XSRF-TOKEN", CSRF);
    }

    MockMvcRequestBuilder json(MockMvcRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    long post(User author) {
        String body = "{\"board\":\"EVENT\",\"targetId\":\"" + EVENT + "\",\"title\":\"제목\",\"body\":\"본문\"}";
        assertThat(as(json(mvc.post().uri("/api/posts"), body), author)).hasStatus(HttpStatus.CREATED);
        return jdbc.sql("SELECT max(id) FROM post").query(Long.class).single();
    }

    MockMvcRequestBuilder report(long postId, User reporter, String reason) {
        return as(json(mvc.post().uri("/api/posts/" + postId + "/report"), "{\"reason\":\"" + reason + "\"}"), reporter);
    }

    // ---------- 신고 ----------

    @Test
    void 한_명이_신고하면_가려지지_않는다() {
        long id = post(user("a"));
        assertThat(report(id, user("b"), "HATE")).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(status(id)).isEqualTo("VISIBLE");
    }

    @Test
    void 서로_다른_두_명이_신고하면_가려진다() {
        long id = post(user("c"));
        assertThat(report(id, user("d"), "HATE")).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(report(id, user("e"), "SPAM")).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(status(id)).isEqualTo("BLINDED");
    }

    @Test
    void 같은_사람은_두_번_신고할_수_없다() {
        long id = post(user("f"));
        User reporter = user("g");
        assertThat(report(id, reporter, "HATE")).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(report(id, reporter, "SPAM")).hasStatus(HttpStatus.CONFLICT);
        assertThat(status(id)).isEqualTo("VISIBLE");
    }

    @Test
    void 본인_글은_신고할_수_없다() {
        User author = user("h");
        long id = post(author);
        assertThat(report(id, author, "HATE")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void 가려진_본인_글은_기타로_재검토를_요청할_수_있다() {
        User author = user("y");
        long id = post(author);
        assertThat(report(id, author, "OTHER")).hasStatus(HttpStatus.BAD_REQUEST);
        sql("UPDATE post SET status = 'BLINDED' WHERE id = :id", id);
        assertThat(report(id, author, "HATE")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(as(json(mvc.post().uri("/api/posts/" + id + "/report"),
                "{\"reason\":\"OTHER\",\"detail\":\"사료 근거가 있는 글입니다\"}"), author))
                .hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void 기타는_이유를_적어야_한다() {
        long id = post(user("i"));
        assertThat(report(id, user("j"), "OTHER")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void CSRF_없이_신고하면_403() {
        long id = post(user("k"));
        User reporter = user("l");
        assertThat(json(mvc.post().uri("/api/posts/" + id + "/report"), "{\"reason\":\"HATE\"}")
                .cookie(new Cookie(ACCESS, reporter.token())))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    // ---------- 관리자 ----------

    @Test
    void 관리자가_아니면_큐를_못_본다() {
        assertThat(as(mvc.get().uri("/api/admin/reports"), user("m"))).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void 관리자에서_내려온_사람의_토큰은_막는다() {
        User admin = admin("n");
        assertThat(as(mvc.get().uri("/api/admin/reports"), admin)).hasStatusOk();
        // 토큰의 role은 아직 ADMIN이지만 DB에서는 USER다
        sql("UPDATE member SET role = 'USER' WHERE id = :id", admin.id());
        assertThat(as(mvc.get().uri("/api/admin/reports"), admin)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void 관리자가_되살리면_보이고_신고가_닫힌다() {
        long id = post(user("o"));
        report(id, user("p"), "HATE").exchange();
        report(id, user("q"), "HATE").exchange();
        assertThat(status(id)).isEqualTo("BLINDED");

        User admin = admin("r");
        assertThat(as(mvc.get().uri("/api/admin/reports"), admin)).hasStatusOk()
                .bodyJson().extractingPath("$[0].reports").isEqualTo(2);
        assertThat(as(json(mvc.put().uri("/api/admin/posts/" + id + "/status"), "{\"status\":\"VISIBLE\"}"), admin))
                .hasStatus(HttpStatus.NO_CONTENT);

        assertThat(status(id)).isEqualTo("VISIBLE");
        assertThat(as(mvc.get().uri("/api/admin/reports"), admin)).hasStatusOk()
                .bodyJson().extractingPath("$.length()").isEqualTo(0);
    }

    @Test
    void 관리자는_신고_없이도_가릴_수_있다() {
        long id = post(user("s"));
        assertThat(as(json(mvc.put().uri("/api/admin/posts/" + id + "/status"), "{\"status\":\"BLINDED\"}"), admin("t")))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(status(id)).isEqualTo("BLINDED");
    }

    @Test
    void 관리자가_제재하면_그_회원은_글을_못_쓴다() {
        User target = user("u");
        assertThat(as(json(mvc.put().uri("/api/admin/members/" + target.id() + "/ban"), "{\"days\":7}"), admin("v")))
                .hasStatus(HttpStatus.NO_CONTENT);
        em.flush();
        em.clear();
        String body = "{\"board\":\"EVENT\",\"targetId\":\"" + EVENT + "\",\"title\":\"t\",\"body\":\"b\"}";
        assertThat(as(json(mvc.post().uri("/api/posts"), body), target)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void 일반_회원은_제재를_걸_수_없다() {
        User target = user("w");
        assertThat(as(json(mvc.put().uri("/api/admin/members/" + target.id() + "/ban"), "{\"days\":7}"), user("x")))
                .hasStatus(HttpStatus.FORBIDDEN);
    }
}
