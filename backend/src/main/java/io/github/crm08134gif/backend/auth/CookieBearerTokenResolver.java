package io.github.crm08134gif.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

// 액세스 토큰을 Authorization 머리글 대신 쿠키에서 꺼낸다
final class CookieBearerTokenResolver implements BearerTokenResolver {

    // 로그인과 상관없는 공개 자료와 토큰 갱신 요청. 만료된 쿠키가 있어도 401 없이 처리되게 토큰을 보지 않는다
    private static final List<String> SKIP = List.of(
        "/api/snapshots", "/api/events", "/api/stories", "/api/votes", "/api/auth/");

    @Override
    public String resolve(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/") || SKIP.stream().anyMatch(path::startsWith)) {
            return null;
        }
        return AuthCookies.read(request, AuthCookies.ACCESS);
    }
}
