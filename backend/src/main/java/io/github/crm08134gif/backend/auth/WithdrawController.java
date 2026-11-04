package io.github.crm08134gif.backend.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;

// 회원 탈퇴. 주소는 /api/me지만 쿠키를 지워야 해서 쿠키를 다루는 auth 패키지에 둔다
// /api/me의 GET만 공개이고 DELETE는 SecurityConfig의 authenticated + CSRF 검사를 받는다
@RestController
class WithdrawController {

    private final TokenService tokenService;

    WithdrawController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @DeleteMapping("/api/me")
    ResponseEntity<Void> withdraw(@AuthenticationPrincipal Jwt jwt, HttpServletResponse response) {
        tokenService.withdraw(Long.valueOf(jwt.getSubject()));
        // 브라우저의 로그인 쿠키도 지워 바로 비로그인 상태가 되게 한다
        AuthCookies.clear(response);
        return ResponseEntity.noContent().build();
    }
}
