package io.github.crm08134gif.backend.auth;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final TokenService tokenService;

    AuthController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    // 액세스 토큰이 만료돼 401을 받은 프론트가 부른다
    @PostMapping("/refresh")
    ResponseEntity<Void> refresh(@CookieValue(name = AuthCookies.REFRESH, required = false) String refreshToken,
                                 @RequestHeader(name = HttpHeaders.USER_AGENT, required = false) String userAgent,
                                 HttpServletResponse response) {
        Optional<IssuedTokens> tokens = refreshToken == null
                ? Optional.empty()
                : tokenService.refresh(refreshToken, userAgent);
        if (tokens.isEmpty()) {
            AuthCookies.clear(response);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        AuthCookies.write(response, tokens.get());
        return ResponseEntity.noContent().build();
    }

    // 이 기기의 로그인을 끊는다. 토큰이 없어도 쿠키는 지운다
    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(name = AuthCookies.REFRESH, required = false) String refreshToken,
                                HttpServletResponse response) {
        if (refreshToken != null) {
            tokenService.logout(refreshToken);
        }
        AuthCookies.clear(response);
        return ResponseEntity.noContent().build();
    }
}
