package io.github.crm08134gif.backend.frontline;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/snapshots")
public class FrontlineSnapshotController {

    private static final int LINE_POINTS = 500;

    private final FrontlineSnapshotRepository repository;

    FrontlineSnapshotController(FrontlineSnapshotRepository repository){
        this.repository = repository;
    }

    @GetMapping
    List<SnapshotResponse> list(){
        return repository.findAll(LINE_POINTS);
    }
}
