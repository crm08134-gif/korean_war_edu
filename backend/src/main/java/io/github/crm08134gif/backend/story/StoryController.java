package io.github.crm08134gif.backend.story;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stories")
public class StoryController {

    private final StoryRepository repository;
    private final boolean includeDrafts;

    // 검수 전 글을 볼지는 사건과 같은 설정(EVENTS_INCLUDE_DRAFTS)을 따른다
    StoryController(StoryRepository repository,
                    @Value("${events.include-drafts}") boolean includeDrafts) {
        this.repository = repository;
        this.includeDrafts = includeDrafts;
    }

    @GetMapping
    List<StoryResponse> list() {
        return repository.findAll(includeDrafts);
    }
}
