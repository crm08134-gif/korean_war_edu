package io.github.crm08134gif.backend.member;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 구현 클래스를 쓰지 않는다. Spring Data가 앱 시작 때 만들어 준다
public interface MemberRepository extends JpaRepository<Member , Long>{

    // 메서드 이름을 읽어 WHERE provider = ? AND provider_user_id = ? 쿼리를 만든다
    Optional<Member> findByProviderAndProviderUserId(String provider, String providerUserId);
}
