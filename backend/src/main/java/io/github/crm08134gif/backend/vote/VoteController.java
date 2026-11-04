package io.github.crm08134gif.backend.vote;

import io.github.crm08134gif.backend.member.Member;
import io.github.crm08134gif.backend.member.MemberService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/votes")
public class VoteController {

    private final VoteRepository repository;
    private final MemberService memberService;

    VoteController(VoteRepository repository, MemberService memberService) {
        this.repository = repository;
        this.memberService = memberService;
    }

    // 숫자는 누구나 본다. 로그인했으면 내 표(mine)도 함께
    @GetMapping
    List<VoteCount> list(@RequestParam String eventId, @AuthenticationPrincipal Jwt jwt) {
        Long memberId = jwt == null ? null : Long.valueOf(jwt.getSubject());
        return repository.counts(eventId, memberId);
    }

    // 누르기는 로그인한 회원만. 비로그인은 SecurityConfig가 여기 오기 전에 401로 막는다
    @PutMapping
    List<VoteCount> vote(@RequestBody VoteRequest request, @AuthenticationPrincipal Jwt jwt) {
        long memberId = voter(jwt);
        switch (request.value()) {
            case 1, -1 -> {
                boolean saved = repository.save(request.eventId(), request.personId(), memberId, request.value());
                if (!saved) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "그 사건에 없는 인물이다");
                }
            }
            case 0 -> repository.delete(request.eventId(), request.personId(), memberId);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "value는 1, -1, 0 가운데 하나다");
        }
        return repository.counts(request.eventId(), memberId);
    }

    // 토큰은 15분 동안 살아 있으므로 탈퇴했는지는 DB에서 다시 본다. 닉네임이 없으면 공감과 같이 막는다
    private long voter(Jwt jwt) {
        Long memberId = Long.valueOf(jwt.getSubject());
        Member member = memberService.find(memberId)
            .filter(m -> !m.isWithdrawn())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (member.needsNickname()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "닉네임을 먼저 정해 주세요");
        }
        return memberId;
    }
}
