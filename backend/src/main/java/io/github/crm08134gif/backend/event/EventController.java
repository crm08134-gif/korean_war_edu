package io.github.crm08134gif.backend.event;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventRepository repository;
    private final boolean includeDrafts;

    EventController(EventRepository repository,
                    @Value("${events.include-drafts}") boolean includeDrafts) {
        this.repository = repository;
        this.includeDrafts = includeDrafts;
    }

    @GetMapping
    List<EventResponse> list() {
        return repository.findAll(includeDrafts);
    }
}
