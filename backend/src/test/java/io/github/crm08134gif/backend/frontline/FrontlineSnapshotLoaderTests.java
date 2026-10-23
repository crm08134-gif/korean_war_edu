package io.github.crm08134gif.backend.frontline;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.crm08134gif.backend.TestcontainersConfiguration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
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

    @Test
    void loadsTitleFromCsv(){
        String title = jdbc.sql("SELECT title FROM frontline_snapshot WHERE snapshot_date = :date")
            .param("date", LocalDate.of(1950, 6, 25))
            .query(String.class)
            .single();
        assertThat(title).isEqualTo("개전");
    }
}
