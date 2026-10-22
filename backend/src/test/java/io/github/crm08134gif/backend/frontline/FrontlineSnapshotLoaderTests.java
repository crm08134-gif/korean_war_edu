package io.github.crm08134gif.backend.frontline;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.crm08134gif.backend.TestcontainersConfiguration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
public class FrontlineSnapshotLoaderTests {

    @Autowired
    JdbcClient jdbc;

    @Test
    void loadsSnapshotsOnStartup(){
        List<LocalDate> dates = jdbc.sql("SELECT snapshot_date FROM frontline_snapshot")
            .query(LocalDate.class)
            .list();
        assertThat(dates).contains(LocalDate.of(1950, 6, 25));
    }
}
