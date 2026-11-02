package io.github.crm08134gif.backend.board;

import io.github.crm08134gif.backend.member.Member;
import io.github.crm08134gif.backend.member.MemberRepository;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BoardService {

    private static final Duration MINUTE = Duration.ofMinutes(1);
    private static final Duration DAY = Duration.ofDays(1);

    private final PostRepository posts;
    private final CommentRepository comments;
    private final LikeRepository likes;
    private final TargetRepository targets;
    private final MemberRepository members;

    BoardService(PostRepository posts, CommentRepository comments, LikeRepository likes,
                 TargetRepository targets, MemberRepository members) {
        this.posts = posts;
        this.comments = comments;
        this.likes = likes;
        this.targets = targets;
        this.members = members;
    }

    // ---------- 글 ----------

    @Transactional
    public long createPost(Long memberId, BoardType board, String targetId, String title, String body) {
        Member author = writer(memberId);
        Instant now = Instant.now();
        if (posts.countByAuthor_IdAndCreatedAtAfter(memberId, now.minus(MINUTE)) >= 1
            || posts.countByAuthor_IdAndCreatedAtAfter(memberId, now.minus(DAY)) >= 20) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "글은 1분에 1개, 하루 20개까지 쓸 수 있습니다");
        }
        if (!targets.exists(board, targetId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "없는 사건이나 인물입니다");
        }
        Post post = posts.save(new Post(board, targetId, author, title.strip(), body.strip(), now));
        return post.getId();
    }

    @Transactional
    public void editPost(Long memberId, long postId, String title, String body) {
        writer(memberId);
        Post post = ownPost(memberId, postId);
        if (post.getStatus() == PostStatus.BLINDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "신고로 가려진 글은 고칠 수 없습니다");
        }
        post.edit(title.strip(), body.strip(), Instant.now());
    }

    @Transactional
    public void deletePost(Long memberId, long postId) {
        activeMember(memberId);
        ownPost(memberId, postId).delete(Instant.now());
    }

    // ---------- 댓글 ----------

    @Transactional
    public long createComment(Long memberId, long postId, String body) {
        Member author = writer(memberId);
        Instant now = Instant.now();
        if (comments.countByAuthor_IdAndCreatedAtAfter(memberId, now.minus(MINUTE)) >= 5) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "댓글은 1분에 5개까지 쓸 수 있습니다");
        }
        Post post = posts.findById(postId)
            .filter(p -> !p.isDeleted())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "글을 찾을 수 없습니다"));
        if (post.getStatus() == PostStatus.BLINDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "가려진 글에는 댓글을 달 수 없습니다");
        }
        return comments.save(new Comment(post, author, body.strip(), now)).getId();
    }

    @Transactional
    public void editComment(Long memberId, long commentId, String body) {
        writer(memberId);
        Comment comment = ownComment(memberId, commentId);
        if (comment.getStatus() == PostStatus.BLINDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "신고로 가려진 댓글은 고칠 수 없습니다");
        }
        comment.edit(body.strip(), Instant.now());
    }

    @Transactional
    public void deleteComment(Long memberId, long commentId) {
        activeMember(memberId);
        ownComment(memberId, commentId).delete(Instant.now());
    }

    // ---------- 공감 ----------

    @Transactional
    public void likePost(Long memberId, long postId, boolean like) {
        activeMember(memberId);
        if (like) {
            likes.likePost(postId, memberId);
        } else {
            likes.unlikePost(postId, memberId);
        }
    }

    @Transactional
    public void likeComment(Long memberId, long commentId, boolean like) {
        activeMember(memberId);
        if (like) {
            likes.likeComment(commentId, memberId);
        } else {
            likes.unlikeComment(commentId, memberId);
        }
    }

    // ---------- 확인 ----------

    // 글·댓글을 쓰거나 고칠 자격. 회원 행을 잠가 같은 회원의 쓰기 요청을 한 줄로 세운다
    private Member writer(Long memberId) {
        Member member = members.findForUpdate(memberId)
            .filter(m -> !m.isWithdrawn())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (member.needsNickname()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "닉네임을 먼저 정해 주세요");
        }
        if (member.isWriteBanned(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "글쓰기가 제한된 계정입니다");
        }
        return member;
    }

    // 지우기·공감 자격. 제재 중이어도 된다
    private Member activeMember(Long memberId) {
        Member member = members.findById(memberId)
            .filter(m -> !m.isWithdrawn())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (member.needsNickname()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "닉네임을 먼저 정해 주세요");
        }
        return member;
    }

    private Post ownPost(Long memberId, long postId) {
        Post post = posts.findById(postId)
            .filter(p -> !p.isDeleted())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "글을 찾을 수 없습니다"));
        if (!post.isWrittenBy(memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 글만 고치거나 지울 수 있습니다");
        }
        return post;
    }

    private Comment ownComment(Long memberId, long commentId) {
        Comment comment = comments.findById(commentId)
            .filter(c -> !c.isDeleted())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다"));
        if (!comment.isWrittenBy(memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 댓글만 고치거나 지울 수 있습니다");
        }
        return comment;
    }
}
