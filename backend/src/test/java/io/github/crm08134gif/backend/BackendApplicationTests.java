package io.github.crm08134gif.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BackendApplicationTests {

    // PostGIS 컨테이너를 띄워 애플리케이션 컨텍스트가 뜨는지만 확인한다
    @Test
    void contextLoads() {
    }

}
