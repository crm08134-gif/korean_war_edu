package io.github.crm08134gif.backend.member;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 구현 클래스를 쓰지 않는다. Spring Data가 앱 시작 때 만들어 준다
public interface MemberRepository extends JpaRepository<Member, Long> {

    // 메서드 이름을 읽어 WHERE provider = ? AND provider_user_id = ? 쿼리를 만든다
    Optional<Member> findByProviderAndProviderUserId(String provider, String providerUserId);

    // 글쓰기 때 회원 행을 잠근다 (SELECT ... FOR UPDATE). 트랜잭션이 끝날 때 풀린다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :id")
    Optional<Member> findForUpdate(Long id);
}
