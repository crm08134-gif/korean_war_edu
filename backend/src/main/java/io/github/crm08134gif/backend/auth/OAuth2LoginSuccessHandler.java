package io.github.crm08134gif.backend.auth;

import io.github.crm08134gif.backend.member.Member;
import io.github.crm08134gif.backend.member.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

// 카카오·구글 로그인이 끝난 뒤: 회원을 찾거나 만들고, 우리 토큰을 쿠키로 심고, 앱 첫 화면으로 보낸다
final class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final MemberService memberService;
    private final TokenService tokenService;
    private final String baseUrl;

    OAuth2LoginSuccessHandler(MemberService memberService, TokenService tokenService, String baseUrl) {
        this.memberService = memberService;
        this.tokenService = tokenService;
        this.baseUrl = baseUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2AuthenticationToken login = (OAuth2AuthenticationToken) authentication;
        // "kakao" 또는 "google", 그리고 그쪽의 회원 번호(카카오 id, 구글 sub)
        Member member = memberService.findOrCreate(login.getAuthorizedClientRegistrationId(), login.getName());
        AuthCookies.write(response, tokenService.login(member, request.getHeader(HttpHeaders.USER_AGENT)));
        // 돌아갈 주소는 고정. 요청에 담긴 주소로 보내지 않는다 (open redirect 방지)
        response.sendRedirect(baseUrl + "/");
    }
}
