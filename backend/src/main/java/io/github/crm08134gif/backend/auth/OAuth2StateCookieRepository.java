package io.github.crm08134gif.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;

// 카카오·구글에 다녀오는 동안의 로그인 요청 정보(state 등)를 세션 대신 서명된 JWT 쿠키에 둔다.
// 자바 직렬화로 쿠키에 넣는 흔한 예제는 사용자가 보낸 바이트를 서버가 역직렬화하게 되어 위험하다. 여기서는 서명을 확인한 글자 값만 읽는다
final class OAuth2StateCookieRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final String COOKIE = "oauth2_login";
    private static final String PATH = "/login/oauth2";
    private static final String AUDIENCE = "oauth2-login";
    private static final Duration LIFETIME = Duration.ofMinutes(5);

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    OAuth2StateCookieRepository(JwtEncoder encoder, SecretKey key) {
        this.encoder = encoder;
        this.decoder = JwtConfig.decoder(key, AUDIENCE);
    }

    // 로그인 시작(카카오로 보내기 직전)
    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest request, HttpServletRequest httpRequest,
                                         HttpServletResponse response) {
        if (request == null) {
            AuthCookies.add(response, COOKIE, "", PATH, Duration.ZERO);
            return;
        }
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .audience(List.of(AUDIENCE))
                .issuedAt(now)
                .expiresAt(now.plus(LIFETIME))
                .claim("authorization_uri", request.getAuthorizationUri())
                .claim("client_id", request.getClientId())
                .claim("redirect_uri", request.getRedirectUri())
                .claim("scopes", List.copyOf(request.getScopes()))
                .claim("state", request.getState())
                .claim("additional_parameters", request.getAdditionalParameters())
                .claim("attributes", request.getAttributes())
                .claim("authorization_request_uri", request.getAuthorizationRequestUri())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        AuthCookies.add(response, COOKIE, value, PATH, LIFETIME);
    }

    // 카카오에서 돌아왔을 때: 쿠키의 서명·만료를 확인하고, 주소의 state가 쿠키의 state와 같을 때만 돌려준다 (login CSRF 방지)
    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String state = request.getParameter(OAuth2ParameterNames.STATE);
        String value = AuthCookies.read(request, COOKIE);
        if (state == null || value == null) {
            return null;
        }
        try {
            Jwt jwt = decoder.decode(value);
            OAuth2AuthorizationRequest saved = OAuth2AuthorizationRequest.authorizationCode()
                    .authorizationUri(jwt.getClaimAsString("authorization_uri"))
                    .clientId(jwt.getClaimAsString("client_id"))
                    .redirectUri(jwt.getClaimAsString("redirect_uri"))
                    .scopes(new LinkedHashSet<>(jwt.getClaimAsStringList("scopes")))
                    .state(jwt.getClaimAsString("state"))
                    .additionalParameters(jwt.getClaimAsMap("additional_parameters"))
                    .attributes(jwt.getClaimAsMap("attributes"))
                    .authorizationRequestUri(jwt.getClaimAsString("authorization_request_uri"))
                    .build();
            return state.equals(saved.getState()) ? saved : null;
        } catch (JwtException e) {
            return null; // 위조·만료된 쿠키는 "로그인 요청 없음"으로 본다
        }
    }

    // 한 번 쓰면 지운다
    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                 HttpServletResponse response) {
        OAuth2AuthorizationRequest saved = loadAuthorizationRequest(request);
        AuthCookies.add(response, COOKIE, "", PATH, Duration.ZERO);
        return saved;
    }
}
