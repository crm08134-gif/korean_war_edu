package io.github.crm08134gif.backend.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

// 인증 쿠키를 쓰고 읽는 곳을 한 군데로 모은다
final class AuthCookies {

    static final String ACCESS = "access_token";
    static final String REFRESH = "refresh_token";
    // 리프레시 토큰은 갱신·로그아웃 요청에만 실려 가게 한다
    private static final String REFRESH_PATH = "/api/auth";

    private AuthCookies() {
    }

    static void write(HttpServletResponse response, IssuedTokens tokens) {
        // 쿠키는 14일 남긴다. 안의 액세스 토큰이 만료되면 서버가 401을 주고 프론트가 갱신한다
        add(response, ACCESS, tokens.accessToken(), "/", TokenService.REFRESH_TOKEN_LIFETIME);
        add(response, REFRESH, tokens.refreshToken(), REFRESH_PATH, TokenService.REFRESH_TOKEN_LIFETIME);
    }

    static void clear(HttpServletResponse response) {
        add(response, ACCESS, "", "/", Duration.ZERO);
        add(response, REFRESH, "", REFRESH_PATH, Duration.ZERO);
    }

    static void add(HttpServletResponse response, String name, String value, String path, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(true)
            .sameSite("Lax")
            .path(path)
            .maxAge(maxAge)
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    static String read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(name)) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
