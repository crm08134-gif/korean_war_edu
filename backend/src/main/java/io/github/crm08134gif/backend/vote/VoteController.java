package io.github.crm08134gif.backend.vote;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/votes")
public class VoteController {

    private final VoteRepository repository;

    VoteController(VoteRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    List<VoteCount> list(@RequestParam String eventId,
                         @RequestHeader("X-Voter-Id") UUID voterId) {
        return repository.counts(eventId, voterId);
    }

    @PutMapping
    List<VoteCount> vote(@RequestBody VoteRequest request,
                         @RequestHeader("X-Voter-Id") UUID voterId) {
        switch (request.value()) {
            case 1, -1 -> {
                boolean saved = repository.save(request.eventId(), request.personId(),voterId, request.value());
                if (!saved) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "그 사건에 없는 인물이다");
                }
            }
            case 0 -> repository.delete(request.eventId(), request.personId(), voterId);
            default -> throw new ResponseStatusException((HttpStatus.BAD_REQUEST), "value는 1, -1, 0 가운데 하나다");
        }
        return repository.counts(request.eventId(), voterId);
    }
}
