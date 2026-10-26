package io.github.crm08134gif.backend.event;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.crm08134gif.backend.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

// 앱이 켜질 때 data/events/의 파일이 DB에 들어갔는지 본다
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class EventLoaderTests {

    @Autowired
    JdbcClient jdbc;

    @Test
    void everyEventHasSources() {
        long withoutSources = jdbc.sql("""
                SELECT count(*) FROM event e
                WHERE NOT EXISTS (SELECT 1 FROM event_source es WHERE es.event_id = e.id)
                """)
            .query(Long.class)
            .single();
        assertThat(withoutSources).isZero();
    }

    @Test
    void storesBlockTypeInBody() {
        long blocks = jdbc.sql("SELECT count(*) FROM event, jsonb_array_elements(body) AS b")
            .query(Long.class)
            .single();
        long withoutType = jdbc.sql("SELECT count(*) FROM event, jsonb_array_elements(body) AS b WHERE b ->> 'type' IS NULL")
            .query(Long.class)
            .single();
        assertThat(blocks).isPositive();
        assertThat(withoutType).isZero();
    }
}
