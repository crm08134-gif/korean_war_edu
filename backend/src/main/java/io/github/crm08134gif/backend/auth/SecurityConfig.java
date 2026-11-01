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
                // 로그아웃은 우리 /api/auth/logout이 한다
                .logout(logout -> logout.disable())
                // 로그인이 필요한데 안 했으면 로그인 화면으로 보내지 않고 401만 준다 (프론트가 처리)
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        // 로그인 없이 읽는 공개 자료
                        .requestMatchers(HttpMethod.GET,
                                "/api/snapshots", "/api/events", "/api/stories", "/api/votes", "/api/me").permitAll()
                        // 인물 투표는 아직 익명 ID로 받는다 (회원 기능 뒤 바꿀 예정)
                        .requestMatchers(HttpMethod.PUT, "/api/votes").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .requestMatchers("/photos/**", "/error", "/actuator/health").permitAll()
                        // API 문서. 배포 전에 닫는다
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        // 위에 없는 주소는 모두 거부. /oauth2/..., /login/oauth2/...는 OAuth 필터가 권한 검사 전에 처리한다
                        .anyRequest().denyAll())
                .oauth2Login(login -> login
                        .authorizationEndpoint(endpoint -> endpoint
                                .authorizationRequestRepository(new OAuth2StateCookieRepository(jwtEncoder, jwtSecretKey)))
                        .authorizedClientRepository(new DiscardingAuthorizedClientRepository())
                        .successHandler(new OAuth2LoginSuccessHandler(memberService, tokenService, baseUrl))
                        // 실패 이유를 주소에 싣지 않는다
                        .failureHandler((request, response, exception) -> response.sendRedirect(baseUrl + "/?login=failed")))
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
