package io.github.crm08134gif.backend.event;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventRepository repository;
    private final boolean includeDrafts;
    private final boolean cacheEnabled;

    // 메모리에 둔 응답. 적재기가 모두 끝난 뒤 한 번 채운다. 비어 있으면(null) DB에서 읽는다
    private volatile List<EventResponse> cached;

    EventController(EventRepository repository,
                    @Value("${events.include-drafts}") boolean includeDrafts,
                    @Value("${public-data.cache}") boolean cacheEnabled) {
        this.repository = repository;
        this.includeDrafts = includeDrafts;
        this.cacheEnabled = cacheEnabled;
    }

    // ApplicationReadyEvent는 적재기(ApplicationRunner)가 모두 끝난 다음에 온다
    @EventListener(ApplicationReadyEvent.class)
    void fillCache() {
        if (cacheEnabled) {
            cached = List.copyOf(repository.findAll(includeDrafts));
        }
    }

    @GetMapping
    List<EventResponse> list() {
        List<EventResponse> result = cached;
        if (result != null) {
            return result;
        }
        return repository.findAll(includeDrafts);
    }
}
