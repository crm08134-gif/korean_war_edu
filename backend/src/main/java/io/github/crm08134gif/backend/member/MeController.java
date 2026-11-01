package io.github.crm08134gif.backend.member;

import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/me")
class MeController {

    private final MemberService memberService;

    MeController(MemberService memberService) {
        this.memberService = memberService;
    }

    // 로그인하지 않았으면 jwt가 null이다
    @GetMapping
    MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return MeResponse.anonymous();
        }
        return memberService.find(Long.valueOf(jwt.getSubject()))
            .filter(member -> !member.isWithdrawn())
            .map(MeResponse::of)
            .orElse(MeResponse.anonymous());
    }

    @PutMapping("/nickname")
    ResponseEntity<Void> changeNickname(@AuthenticationPrincipal Jwt jwt,
                                        @Valid @RequestBody NicknameRequest request) {
        try {
            memberService.changeNickname(Long.valueOf(jwt.getSubject()), request.nickname());
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 쓰고 있는 닉네임입니다");
        }
        return ResponseEntity.noContent().build();
    }
}
