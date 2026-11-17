package io.github.crm08134gif.backend.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.github.crm08134gif.backend.TestcontainersConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

// AI 전체 하루 한도가 동시 요청에서도 지켜지는지 본다.
// 스레드마다 따로 커밋해야 서로의 횟수가 보이므로 @Transactional을 붙이지 않고, 끝나면 직접 지운다
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "chat.global-daily-limit=" + ChatConcurrencyTests.GLOBAL_LIMIT)
class ChatConcurrencyTests {

    static final int GLOBAL_LIMIT = 10;
    private static final int MEMBERS = 30;

    @Autowired
    ChatService chatService;

    @Autowired
    JdbcClient jdbc;

    // 진짜 구글을 부르지 않는다. 무료 한도를 쓰지 않고, 세는 로직만 본다
    @MockitoBean
    GeminiClient gemini;

    private final List<Long> memberIds = new ArrayList<>();
    private String eventId;

    @BeforeEach
    void setUp() {
        when(gemini.isConfigured()).thenReturn(true);
        when(gemini.generate(anyString(), any())).thenReturn("답");

        jdbc.sql("DELETE FROM chat_usage").update();
        // 질문할 카드. 앱이 켜질 때 적재기가 넣은 검수된 사건 하나를 쓴다
        eventId = jdbc.sql("SELECT id FROM event WHERE status = 'reviewed' ORDER BY id LIMIT 1")
                .query(String.class)
                .single();
        for (int i = 0; i < MEMBERS; i++) {
            Long id = jdbc.sql("""
                    INSERT INTO member (provider, provider_user_id, nickname, role, created_at)
                    VALUES ('kakao', :userId, :nickname, 'USER', now())
                    RETURNING id
                    """)
                    .param("userId", "concurrency-" + i)
                    .param("nickname", "동시성" + i)
                    .query(Long.class)
                    .single();
            memberIds.add(id);
        }
    }

    @AfterEach
    void tearDown() {
        jdbc.sql("DELETE FROM chat_usage").update();
        jdbc.sql("DELETE FROM member WHERE provider_user_id LIKE 'concurrency-%'").update();
    }

    @Test
    void globalDailyLimitHoldsUnderConcurrentRequests() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(MEMBERS);
        CountDownLatch ready = new CountDownLatch(MEMBERS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(MEMBERS);
        AtomicInteger answered = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        for (Long memberId : memberIds) {
            // 스레드에 할 일을 넘기는 자리라 람다를 쓴다
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    chatService.ask(memberId, new ChatRequest("event", eventId, List.of(), "질문"));
                    answered.incrementAndGet();
                } catch (ResponseStatusException e) {
                    rejected.incrementAndGet();
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

        long total = jdbc.sql("SELECT COALESCE(sum(used), 0) FROM chat_usage")
                .query(Long.class)
                .single();
        assertThat(answered.get() + rejected.get()).isEqualTo(MEMBERS);
        assertThat(total).isEqualTo(answered.get());
        assertThat(total).isLessThanOrEqualTo(GLOBAL_LIMIT);
    }
}
