package io.github.crm08134gif.backend.auth;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long>{

    // 쿠키로 온 토큰을 해시해서 찾는다.
    // 같은 토큰으로 갱신 요청이 동시에 와도 하나씩 처리되게 행을 잠근다 (SELECT ... FOR UPDATE)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // 한 기기 묶음의 살아 있는 토큰을 한 번에 폐기한다
    // clearAutomatically: 이 UPDATE는 영속성 컨텍스트를 거치지 않으므로, 끝나고 컨텍스트를 비워 옛 값을 다시 쓰지 않게 한다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.familyId = :familyId AND t.revokedAt IS NULL")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

    // 탈퇴: 이 회원의 로그인 기록을 모두 지운다. 기기 정보(브라우저 이름)도 개인정보라 폐기 표시가 아니라 삭제한다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM RefreshToken t WHERE t.member.id = :memberId")
    int deleteByMember(@Param("memberId") Long memberId);
}
