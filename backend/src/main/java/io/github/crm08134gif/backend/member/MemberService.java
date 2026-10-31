package io.github.crm08134gif.backend.member;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MemberService {
    // 운영진이나 탈퇴 표시로 보이는 닉네임은 막는다. 나머지 금칙어는 3단계 운영 정책과 함께
    private static final List<String> RESERVED = List.of("관리자", "운영자", "admin", "탈퇴");

    private final MemberRepository members;

    MemberService(MemberRepository members) {
        this.members = members;
    }

    // 소셜 로그인 성공 때. 처음 온 사람이면 회원을 만든다
    @Transactional
    public Member findOrCreate(String provider, String providerUserId) {
        return members.findByProviderAndProviderUserId(provider, providerUserId)
            .orElseGet(() -> members.save(new Member(provider, providerUserId)));
    }

    @Transactional(readOnly = true)
    public Optional<Member> find(Long id) {
        return members.findById(id);
    }

    @Transactional
    public void changeNickname(Long memberId, String nickname) {
        // 토큰은 아직 살아 있어도(최대 15분) 탈퇴한 회원은 여기서 막는다
        Member member = members.findById(memberId)
            .filter(m -> !m.isWithdrawn())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        String lower = nickname.toLowerCase(Locale.ROOT);
        if (RESERVED.stream().anyMatch(lower::contains)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "쓸 수 없는 닉네임입니다");
        }
        Instant now = Instant.now();
        if (!member.canChangeNickname(now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "닉네임은 30일에 한 번 바꿀 수 있습니다");
        }
        member.changeNickname(nickname, now);
        // 원래는 트랜잭션이 끝날 때 UPDATE가 나간다. 중복 닉네임 오류를 이 메서드 안에서 받으려고 지금 보낸다
        members.flush();
    }
}
