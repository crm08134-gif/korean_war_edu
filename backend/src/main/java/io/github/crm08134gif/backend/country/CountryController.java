package io.github.crm08134gif.backend.country;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 참전국·의료지원국 목록. 로그인 없이 읽는다 (SecurityConfig의 공개 자료)
@RestController
@RequestMapping("/api/countries")
class CountryController {

    private final CountryCatalog catalog;
    private final boolean includeDrafts;

    // 검수 전 자료를 볼지는 사건과 같은 설정(EVENTS_INCLUDE_DRAFTS)을 따른다
    CountryController(CountryCatalog catalog, @Value("${events.include-drafts}") boolean includeDrafts) {
        this.catalog = catalog;
        this.includeDrafts = includeDrafts;
    }

    @GetMapping
    List<CountryResponse> list() {
        return catalog.list(includeDrafts);
    }
}
