package io.github.crm08134gif.backend.board;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 관리자 큐. /api/admin/**은 SecurityConfig가 토큰의 ADMIN만 들여보내고, 서비스가 DB 역할을 다시 본다
@RestController
@RequestMapping("/api/admin")
class AdminController {

    private final ReportService service;

    AdminController(ReportService service) {
        this.service = service;
    }

    @GetMapping("/reports")
    List<QueueItem> queue(@AuthenticationPrincipal Jwt jwt) {
        return service.queue(adminId(jwt));
    }

    @PutMapping("/posts/{id}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void moderatePost(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                      @Valid @RequestBody ModerationRequest request) {
        service.moderate(adminId(jwt), ReportTarget.POST, id, request.status());
    }

    @PutMapping("/comments/{id}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void moderateComment(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                         @Valid @RequestBody ModerationRequest request) {
        service.moderate(adminId(jwt), ReportTarget.COMMENT, id, request.status());
    }

    @PutMapping("/members/{id}/ban")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void ban(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody BanRequest request) {
        service.ban(adminId(jwt), id, request.days());
    }

    private static Long adminId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
