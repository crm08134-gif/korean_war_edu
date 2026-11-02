package io.github.crm08134gif.backend.board;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 요청을 받아 검사하고 서비스에 넘기기만 한다. 규칙은 모두 BoardService에 있다
@RestController
@RequestMapping("/api")
class BoardController {

    private final BoardService service;
    private final ReportService reports;

    BoardController(BoardService service, ReportService reports) {
        this.service = service;
        this.reports = reports;
    }

    // ---------- 읽기 (공개) ----------

    // targetId가 없으면 드롭다운 "전체"
    @GetMapping("/posts")
    PostPage list(@RequestParam BoardType board,
                  @RequestParam(required = false) String targetId,
                  @RequestParam(required = false) Long cursor,
                  @AuthenticationPrincipal Jwt jwt) {
        return service.list(board, targetId, cursor, viewer(jwt));
    }

    @GetMapping("/posts/{id}")
    PostDetail detail(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return service.detail(id, viewer(jwt));
    }

    // ---------- 글 ----------

    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    IdResponse createPost(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PostRequest request) {
        long id = service.createPost(memberId(jwt), request.board(), request.targetId(),
            request.title(), request.body());
        return new IdResponse(id);
    }

    @PatchMapping("/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void editPost(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                  @Valid @RequestBody PostEditRequest request) {
        service.editPost(memberId(jwt), id, request.title(), request.body());
    }

    @DeleteMapping("/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deletePost(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        service.deletePost(memberId(jwt), id);
    }

    // ---------- 댓글 ----------

    @PostMapping("/posts/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    IdResponse createComment(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                             @Valid @RequestBody CommentRequest request) {
        return new IdResponse(service.createComment(memberId(jwt), id, request.body()));
    }

    @PatchMapping("/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void editComment(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                     @Valid @RequestBody CommentRequest request) {
        service.editComment(memberId(jwt), id, request.body());
    }

    @DeleteMapping("/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteComment(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        service.deleteComment(memberId(jwt), id);
    }

    // ---------- 공감: PUT은 누름, DELETE는 취소 (두 번 보내도 결과가 같다) ----------

    @PutMapping("/posts/{id}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void likePost(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        service.likePost(memberId(jwt), id, true);
    }

    @DeleteMapping("/posts/{id}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unlikePost(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        service.likePost(memberId(jwt), id, false);
    }

    @PutMapping("/comments/{id}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void likeComment(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        service.likeComment(memberId(jwt), id, true);
    }

    @DeleteMapping("/comments/{id}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unlikeComment(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        service.likeComment(memberId(jwt), id, false);
    }

    // ---------- 신고 (서로 다른 회원 2명이면 자동으로 가려진다) ----------

    @PostMapping("/posts/{id}/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reportPost(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                    @Valid @RequestBody ReportRequest request) {
        reports.report(memberId(jwt), ReportTarget.POST, id, request.reason(), request.detail());
    }

    @PostMapping("/comments/{id}/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reportComment(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                       @Valid @RequestBody ReportRequest request) {
        reports.report(memberId(jwt), ReportTarget.COMMENT, id, request.reason(), request.detail());
    }

    // 공개 읽기: 로그인하지 않았으면 null
    private static Long viewer(Jwt jwt) {
        return jwt == null ? null : Long.valueOf(jwt.getSubject());
    }

    // 쓰기: SecurityConfig가 로그인한 요청만 들여보내므로 jwt가 늘 있다
    private static Long memberId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
