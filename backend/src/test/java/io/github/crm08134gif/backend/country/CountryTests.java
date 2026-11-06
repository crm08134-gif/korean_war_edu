package io.github.crm08134gif.backend.country;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.crm08134gif.backend.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

// 참전국 목록이 공개로 읽히고, 실제 CSV가 검사를 통과하며, 잘못된 줄은 막히는지 확인한다
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CountryTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    CountryCatalog catalog;

    @Test
    void 로그인_없이_읽는다() {
        assertThat(mvc.get().uri("/api/countries")).hasStatus(HttpStatus.OK);
    }

    @Test
    void 참전국_16개와_의료지원국_6개() {
        List<CountryResponse> all = catalog.list(true);
        int combat = 0;
        int medical = 0;
        for (CountryResponse country : all) {
            if ("combat".equals(country.group())) {
                combat++;
            } else if ("medical".equals(country.group())) {
                medical++;
            }
        }
        assertThat(combat).isEqualTo(16);
        assertThat(medical).isEqualTo(6);
    }

    @Test
    void 전사자가_사상자보다_많으면_막는다() {
        CountryRow wrong = new CountryRow("xx", "가짜", "combat", null, 100, 50, 10, null, null, "draft");
        assertThatThrownBy(() -> CountryCatalog.check(List.of(wrong))).hasMessageContaining("사상자보다");
    }

    @Test
    void 참전국에_숫자가_빠지면_막는다() {
        CountryRow wrong = new CountryRow("xx", "가짜", "combat", null, 100, null, 10, null, null, "draft");
        assertThatThrownBy(() -> CountryCatalog.check(List.of(wrong))).hasMessageContaining("모두 있어야");
    }

    @Test
    void 같은_나라가_두_번_나오면_막는다() {
        CountryRow one = new CountryRow("xx", "가짜", "medical", null, 100, null, null, "병원", null, "draft");
        assertThatThrownBy(() -> CountryCatalog.check(List.of(one, one))).hasMessageContaining("두 번");
    }
}
