package io.github.crm08134gif.backend.frontline;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.crm08134gif.backend.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class FrontlineSnapshotControllerTests {

    @Autowired
    MockMvcTester mvc;

    @Test
    void listsSnapshotWithResampledLines() {
        MvcTestResult result = mvc.get().uri("/api/snapshots").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$[0].snapshotDate").isEqualTo("1950-06-25");
        assertThat(result).bodyJson().extractingPath("$[0].title").isEqualTo("개전");
        assertThat(result).bodyJson().extractingPath("$[0].frontLine.coordinates").asArray().hasSize(500);
    }

}
