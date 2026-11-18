package io.github.crm08134gif.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.crm08134gif.backend.TestcontainersConfiguration;
import io.github.crm08134gif.backend.member.Member;
import io.github.crm08134gif.backend.member.MemberRepository;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

// 같은 리프레시 토큰으로 갱신 요청이 동시에 와도 새 토큰은 딱 한 번만 나가는지 본다.
// 스레드마다 따로 커밋해야 서로가 보이므로 @Transactional을 붙이지 않고, 끝나면 직접 지운다
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RefreshTokenConcurrencyTests {

    private static final int REQUESTS = 10;
    private static final String PROVIDER_USER_ID = "refresh-concurrency";

    @Autowired
    TokenService tokenService;

    @Autowired
    MemberRepository members;

    @Autowired
    JdbcClient jdbc;

    @AfterEach
    void tearDown() {
        jdbc.sql("""
                DELETE FROM refresh_token
                WHERE member_id IN (SELECT id FROM member WHERE provider_user_id = :id)
                """)
                .param("id", PROVIDER_USER_ID)
                .update();
        jdbc.sql("DELETE FROM member WHERE provider_user_id = :id")
                .param("id", PROVIDER_USER_ID)
                .update();
    }

    @Test
    void sameRefreshTokenIsRotatedOnlyOnceUnderConcurrentRequests() throws InterruptedException {
        Member member = members.save(new Member("kakao", PROVIDER_USER_ID));
        String token = tokenService.login(member, "test").refreshToken();

        ExecutorService pool = Executors.newFixedThreadPool(REQUESTS);
        CountDownLatch ready = new CountDownLatch(REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(REQUESTS);
        AtomicInteger failed = new AtomicInteger();
        // 성공한 요청이 받은 새 리프레시 토큰. 여러 스레드가 넣으므로 스레드에 안전한 큐를 쓴다
        ConcurrentLinkedQueue<String> issued = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < REQUESTS; i++) {
            // 스레드에 할 일을 넘기는 자리라 람다를 쓴다
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    Optional<IssuedTokens> result = tokenService.refresh(token, "test");
                    if (result.isPresent()) {
                        issued.add(result.get().refreshToken());
                    } else {
                        failed.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        // 새 토큰은 한 번만 나가고, 나머지는 모두 실패한다
        assertThat(issued).hasSize(1);
        assertThat(failed.get()).isEqualTo(REQUESTS - 1);
        // 나머지 요청은 "이미 쓴 토큰이 또 왔다"로 보고 묶음을 끊었으므로, 새로 받은 토큰도 이미 폐기됐다
        assertThat(tokenService.refresh(issued.peek(), "test")).isEmpty();
    }
}
