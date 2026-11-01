package io.github.crm08134gif.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.crm08134gif.backend.TestcontainersConfiguration;
import io.github.crm08134gif.backend.member.Member;
import io.github.crm08134gif.backend.member.MemberRepository;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.annotation.Transactional;

// 로그인·토큰·CSRF가 공격을 막는지 확인한다
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthSecurityTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    MemberRepository members;

    @Autowired
    TokenService tokenService;

    Member newMember(String providerUserId) {
        return members.save(new Member("kakao", providerUserId));
    }

    Cookie access(String token) {
        return new Cookie(AuthCookies.ACCESS, token);
    }

    // 프론트처럼 CSRF 쿠키 값과 같은 값을 머리글에 넣어 보낸다
    MockMvcTester.MockMvcRequestBuilder putNickname(String accessToken, String nickname, boolean withCsrf) {
        MockMvcTester.MockMvcRequestBuilder request = mvc.put().uri("/api/me/nickname")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"" + nickname + "\"}");
        if (withCsrf) {
            return request.cookie(access(accessToken), new Cookie("XSRF-TOKEN", "test-csrf"))
                    .header("X-XSRF-TOKEN", "test-csrf");
        }
        return request.cookie(access(accessToken));
    }

    @Test
    void 로그인하지_않으면_비로그인으로_답한다() {
        assertThat(mvc.get().uri("/api/me")).hasStatusOk()
                .bodyJson().extractingPath("$.loggedIn").isEqualTo(false);
    }

    @Test
    void 올바른_토큰이면_로그인으로_답한다() {
        IssuedTokens tokens = tokenService.login(newMember("t1"), "test");
        assertThat(mvc.get().uri("/api/me").cookie(access(tokens.accessToken()))).hasStatusOk()
                .bodyJson().extractingPath("$.needsNickname").isEqualTo(true);
    }

    @Test
    void 다른_키로_서명한_토큰은_거부한다() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER).audience(List.of("api")).subject("1").claim("role", "ADMIN")
                .issuedAt(now).expiresAt(now.plusSeconds(600)).build();
        String forged = NimbusJwtEncoder.withSecretKey(new SecretKeySpec(new byte[32], "HmacSHA256"))
                .algorithm(MacAlgorithm.HS256).build()
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        assertThat(mvc.get().uri("/api/me").cookie(access(forged))).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void 공개_자료는_망가진_토큰이_있어도_읽힌다() {
        assertThat(mvc.get().uri("/api/events").cookie(access("broken"))).hasStatusOk();
    }

    @Test
    void CSRF_토큰이_없으면_쓰기를_거부한다() {
        IssuedTokens tokens = tokenService.login(newMember("t2"), "test");
        assertThat(putNickname(tokens.accessToken(), "기록병", false)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void 닉네임을_정하고_중복은_거부한다() {
        IssuedTokens first = tokenService.login(newMember("t3"), "test");
        IssuedTokens second = tokenService.login(newMember("t4"), "test");
        assertThat(putNickname(first.accessToken(), "기록병", true)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(putNickname(second.accessToken(), "기록병", true)).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void 로그인하지_않고_쓰면_401() {
        assertThat(mvc.put().uri("/api/me/nickname")
                .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"기록병\"}")
                .cookie(new Cookie("XSRF-TOKEN", "test-csrf")).header("X-XSRF-TOKEN", "test-csrf"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void 리프레시_토큰을_다시_쓰면_그_기기의_로그인이_모두_끊긴다() {
        IssuedTokens first = tokenService.login(newMember("t5"), "test");
        IssuedTokens second = tokenService.refresh(first.refreshToken(), "test").orElseThrow();
        // 이미 쓴 토큰을 다시 쓰면 실패하고
        assertThat(tokenService.refresh(first.refreshToken(), "test")).isEmpty();
        // 그 뒤에 받은 정상 토큰까지 함께 끊긴다
        assertThat(tokenService.refresh(second.refreshToken(), "test")).isEmpty();
    }
}
