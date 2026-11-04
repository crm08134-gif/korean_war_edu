package io.github.crm08134gif.backend.auth;

import io.github.crm08134gif.backend.member.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CsrfFilter;

@Configuration
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtEncoder jwtEncoder, SecretKey jwtSecretKey,
                                            MemberService memberService, TokenService tokenService,
                                            @Value("${auth.base-url}") String baseUrl) throws Exception {
        // 이 파일의 "이름 -> 할 일"은 람다다. Spring Security 7은 설정을 이 모양으로만 받는다 (예전의 .and() 방식은 없어졌다).
        //   .sessionManagement(session -> session.sessionCreationPolicy(...))
        //   = "세션 설정 상자(session)를 줄 테니, 그 상자에 화살표 오른쪽 일을 해 달라"
        //   화살표 왼쪽 이름(session, csrf, auth ...)은 우리가 붙이는 이름이라 아무 이름이나 된다.
        //   오른쪽이 여러 줄이면 { } 로 묶는다 (아래 csrf)
        http
                // 서버 세션을 만들지 않는다. 누구인지는 요청마다 쿠키의 JWT로 판단한다
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CSRF: XSRF-TOKEN 쿠키 값을 프론트가 X-XSRF-TOKEN 머리글로 다시 보내야 쓰기 요청이 통과한다
                .csrf(csrf -> {
                    csrf.spa();
                    // 아래 oauth2ResourceServer 설정은 "토큰이 실린 요청"을 CSRF 검사에서 저절로 뺀다.
                    // 토큰을 Authorization 머리글로 보낼 때는 맞지만(브라우저가 알아서 붙이지 않는다),
                    // 우리는 쿠키로 보내서 브라우저가 다른 사이트의 요청에도 실어 보낸다. 그래서 모든 쓰기 요청을 다시 검사하게 되돌린다
                    // (AuthSecurityTests의 CSRF 테스트가 이 구멍을 잡았다)
                    csrf.addObjectPostProcessor(new ObjectPostProcessor<CsrfFilter>() {
                        @Override
                        public <O extends CsrfFilter> O postProcess(O filter) {
                            filter.setRequireCsrfProtectionMatcher(CsrfFilter.DEFAULT_CSRF_MATCHER);
                            return filter;
                        }
                    });
                })
                // 로그아웃은 우리 /api/auth/logout이 한다. Spring 기본 로그아웃 주소(/logout)는 끈다
                .logout(logout -> logout.disable())
                // 로그인이 필요한데 안 했으면 로그인 화면으로 보내지 않고 401만 준다 (프론트가 처리)
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                // 주소마다 누가 들어올 수 있는지. 위에서부터 차례로 보고 처음 맞는 줄을 쓴다
                .authorizeHttpRequests(auth -> auth
                        // 로그인 없이 읽는 공개 자료
                        .requestMatchers(HttpMethod.GET,
                                "/api/snapshots", "/api/events", "/api/stories", "/api/votes", "/api/me").permitAll()
                        // 게시판 읽기는 공개. GET만 연다 (같은 주소의 POST·PATCH·DELETE는 아래 authenticated에 걸린다)
                        // CookieBearerTokenResolver의 건너뛰기 목록에는 넣지 않는다: "내 글", "내 공감", 가려진 내 글 내용에 로그인 정보가 필요하다
                        // "/*"는 경로 한 칸만. /api/posts/5는 열리고 /api/posts/5/comments는 안 열린다
                        .requestMatchers(HttpMethod.GET, "/api/posts", "/api/posts/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .requestMatchers("/photos/**", "/error", "/actuator/health").permitAll()
                        // API 문서. 배포 전에 닫는다
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        // 위에 없는 주소는 모두 거부. /oauth2/..., /login/oauth2/...는 OAuth 필터가 권한 검사 전에 처리한다
                        .anyRequest().denyAll())
                // 카카오·구글 로그인: 도중 상태는 쿠키에, 제공자 토큰은 버리고, 성공하면 우리 토큰을 발급한다
                .oauth2Login(login -> login
                        .authorizationEndpoint(endpoint -> endpoint
                                .authorizationRequestRepository(new OAuth2StateCookieRepository(jwtEncoder, jwtSecretKey)))
                        .authorizedClientRepository(new DiscardingAuthorizedClientRepository())
                        .successHandler(new OAuth2LoginSuccessHandler(memberService, tokenService, baseUrl))
                        // 실패 이유를 주소에 싣지 않는다.
                        // 람다가 값 셋(요청, 응답, 실패 이유)을 받는다. 실패 이유(exception)는 쓰지 않고 첫 화면으로 돌려보낸다
                        .failureHandler((request, response, exception) -> response.sendRedirect(baseUrl + "/?login=failed")))
                // API 요청마다: 쿠키에서 액세스 토큰을 꺼내(CookieBearerTokenResolver) 검사하고, role을 권한으로 바꾼다
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(new CookieBearerTokenResolver())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    // JWT의 role 값(USER, ADMIN)을 Spring 권한 ROLE_USER, ROLE_ADMIN으로 바꾼다. hasRole("ADMIN")이 이걸 본다
    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    // 카카오·구글이 준 토큰을 저장하지 않는다. 회원 번호만 받으면 더 쓸 일이 없다
    // (기본값은 서버 메모리에 로그인한 사람마다 계속 쌓아 둔다)
    private static final class DiscardingAuthorizedClientRepository implements OAuth2AuthorizedClientRepository {

        @Override
        public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String clientRegistrationId,
                                                                         Authentication principal,
                                                                         HttpServletRequest request) {
            return null;
        }

        @Override
        public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal,
                                         HttpServletRequest request, HttpServletResponse response) {
        }

        @Override
        public void removeAuthorizedClient(String clientRegistrationId, Authentication principal,
                                           HttpServletRequest request, HttpServletResponse response) {
        }
    }
}
