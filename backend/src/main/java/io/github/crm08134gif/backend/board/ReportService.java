package io.github.crm08134gif.backend.board;

import io.github.crm08134gif.backend.member.Member;
import io.github.crm08134gif.backend.member.MemberRepository;
import io.github.crm08134gif.backend.member.Role;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// 신고와 관리자 처리. 가리기·되살리기 규칙을 한곳에 둔다
@Service
public class ReportService {

    // 서로 다른 회원 이만큼이 신고하면 자동으로 가린다 (사용자 결정, CLAUDE.md 7장)
    static final int BLIND_THRESHOLD = 2;
    private static final int DAILY_LIMIT = 20;
    // 제재는 1년까지. 그보다 길게 막아야 하면 다시 건다
    private static final int MAX_BAN_DAYS = 365;

    private final ReportRepository reports;
    private final PostRepository posts;
    private final CommentRepository comments;
    private final MemberRepository members;

    ReportService(ReportRepository reports, PostRepository posts, CommentRepository comments,
                  MemberRepository members) {
        this.reports = reports;
        this.posts = posts;
        this.comments = comments;
        this.members = members;
    }

    // ---------- 신고 ----------

    @Transactional
    public void report(Long memberId, ReportTarget type, long targetId, ReportReason reason, String detail) {
        reporter(memberId);
        String cleanDetail = detail == null || detail.isBlank() ? null : detail.strip();
        if (reason == ReportReason.OTHER && cleanDetail == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "기타를 고르면 이유를 적어 주세요");
        }
        if (reports.countToday(memberId) >= DAILY_LIMIT) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "신고는 하루 20건까지 할 수 있습니다");
        }

        // 대상 행을 잠근다. 이 트랜잭션이 끝날 때까지 같은 대상의 다른 신고는 기다린다.
        // 그래서 "이미 신고했나"와 "2명이 찼나"를 세는 사이에 끼어드는 요청이 없다
        Moderatable target = lock(type, targetId, false);
        // 본인 글은 신고할 수 없다. 단 가려진 본인 글은 "기타"로 재검토를 요청할 수 있다 (운영 정책의 이의 제기).
        // 이 요청은 관리자 큐에 오를 뿐이고, 이미 가려진 글이라 2명 기준에는 영향이 없다
        if (target.isWrittenBy(memberId)
                && !(target.getStatus() == PostStatus.BLINDED && reason == ReportReason.OTHER)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "본인 글은 신고할 수 없습니다");
        }
        if (reports.exists(type, targetId, memberId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 신고했습니다");
        }
        reports.insert(type, targetId, memberId, reason, cleanDetail);

        if (reports.countOpen(type, targetId) >= BLIND_THRESHOLD) {
            // 엔티티 필드만 바꾼다. 트랜잭션이 끝날 때 UPDATE가 나간다 (변경 감지)
            target.blind();
        }
    }

    // ---------- 관리자 ----------

    @Transactional(readOnly = true)
    public List<QueueItem> queue(Long adminId) {
        admin(adminId);
        return reports.queue();
    }

    // 되살리기(VISIBLE) 또는 가리기(BLINDED). 그 대상의 처리 안 된 신고를 모두 닫는다.
    // 신고가 없어도 가릴 수 있다: 권리 침해 당사자의 요청은 관리자가 직접 가린다 (CLAUDE.md 7장)
    @Transactional
    public void moderate(Long adminId, ReportTarget type, long targetId, PostStatus status) {
        Member admin = admin(adminId);
        // 작성자가 이미 지운 글도 큐에 남아 있을 수 있다. 그때는 상태는 그대로 두고(restore·blind가 지운 것은 건드리지 않는다) 신고만 닫는다
        Moderatable target = lock(type, targetId, true);
        switch (status) {
            case VISIBLE -> target.restore();
            case BLINDED -> target.blind();
            case DELETED -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "되살리기나 가리기만 할 수 있습니다");
        }
        reports.close(type, targetId, admin.getId(), status);
    }

    // 글쓰기 제재. days가 0이면 푼다. 제재 중에도 읽기·본인 글 지우기는 된다
    @Transactional
    public void ban(Long adminId, long memberId, int days) {
        admin(adminId);
        if (days < 0 || days > MAX_BAN_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "제재 기간은 0~365일입니다");
        }
        Member member = members.findById(memberId).orElse(null);
        if (member == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "없는 회원입니다");
        }
        member.banWritingUntil(days == 0 ? null : Instant.now().plus(Duration.ofDays(days)));
    }

    // ---------- 확인 ----------

    private Moderatable lock(ReportTarget type, long targetId, boolean allowDeleted) {
        Moderatable target = switch (type) {
            case POST -> posts.findForUpdate(targetId).orElse(null);
            case COMMENT -> comments.findForUpdate(targetId).orElse(null);
        };
        if (target == null || (target.isDeleted() && !allowDeleted)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "글을 찾을 수 없습니다");
        }
        return target;
    }

    // 신고할 자격: 로그인, 닉네임, 탈퇴·제재 아님 (제재 중인 사람이 신고를 쏟아 내는 것을 막는다)
    private Member reporter(Long memberId) {
        Member member = members.findById(memberId).orElse(null);
        if (member == null || member.isWithdrawn()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (member.needsNickname()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "닉네임을 먼저 정해 주세요");
        }
        if (member.isWriteBanned(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "제재 중에는 신고할 수 없습니다");
        }
        return member;
    }

    // 토큰의 role(ADMIN)은 SecurityConfig가 이미 봤다. 토큰은 15분 동안 살아 있으므로
    // 그 사이 관리자에서 내려온 사람을 막으려고 DB의 역할을 다시 본다
    private Member admin(Long memberId) {
        Member member = members.findById(memberId).orElse(null);
        if (member == null || member.isWithdrawn() || member.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return member;
    }
}
