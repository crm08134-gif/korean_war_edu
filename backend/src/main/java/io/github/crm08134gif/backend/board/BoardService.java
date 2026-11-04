package io.github.crm08134gif.backend.board;

import io.github.crm08134gif.backend.member.Member;
import io.github.crm08134gif.backend.member.MemberRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BoardService {

    private static final Duration MINUTE = Duration.ofMinutes(1);
    private static final Duration DAY = Duration.ofDays(1);
    private static final int PAGE_SIZE = 20;
    private static final String WITHDRAWN = "탈퇴한 사용자";

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

    // ---------- 읽기 (로그인 없이도. viewerId는 비로그인이면 null) ----------

    // targetId가 null이면 드롭다운 "전체"
    @Transactional(readOnly = true)
    public PostPage list(BoardType board, String targetId, Long cursor, Long viewerId) {
        long before = cursor == null ? Long.MAX_VALUE : cursor;
        // 하나 더 읽어서 다음 페이지가 있는지 본다 (COUNT 쿼리 없이)
        Limit limit = Limit.of(PAGE_SIZE + 1);
        List<Post> rows = targetId == null
            ? posts.findBoardPage(board, before, limit)
            : posts.findPage(board, targetId, before, limit);

        boolean hasNext = rows.size() > PAGE_SIZE;
        List<Post> page = hasNext ? rows.subList(0, PAGE_SIZE) : rows;

        // 공감 수는 글 개수와 상관없이 쿼리 한 번
        List<Long> postIds = new ArrayList<>();
        for (Post p : page) {
            postIds.add(p.getId());
        }
        Map<Long, LikeCount> counts = likes.postCounts(postIds, viewerId);

        List<PostSummary> items = new ArrayList<>();
        for (Post p : page) {
            items.add(summary(p, counts.get(p.getId()), viewerId));
        }
        Long nextCursor = hasNext ? page.getLast().getId() : null;
        return new PostPage(items, nextCursor);
    }

    @Transactional(readOnly = true)
    public PostDetail detail(long postId, Long viewerId) {
        Post post = posts.findWithAuthor(postId).orElse(null);
        if (post == null || post.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "글을 찾을 수 없습니다");
        }
        List<Comment> rows = comments.findByPost(postId);

        LikeCount postLikes = likes.postCounts(List.of(postId), viewerId).get(postId);
        List<Long> commentIds = new ArrayList<>();
        for (Comment c : rows) {
            commentIds.add(c.getId());
        }
        Map<Long, LikeCount> commentLikes = likes.commentCounts(commentIds, viewerId);

        boolean mine = post.isWrittenBy(viewerId);
        boolean blinded = post.getStatus() == PostStatus.BLINDED;
        // 가려진 글은 화면에서 숨기는 게 아니라 서버가 내용을 아예 보내지 않는다. 작성자 본인에게만 보낸다
        boolean hidden = blinded && !mine;
        List<CommentView> views = new ArrayList<>();
        for (Comment c : rows) {
            views.add(view(c, commentLikes.get(c.getId()), viewerId));
        }

        // 인수 순서는 PostDetail의 칸 순서와 같아야 한다 (같은 String끼리는 바뀌어도 컴파일 오류가 안 난다)
        return new PostDetail(
            post.getId(), post.getBoard(), post.getTargetId(),
            hidden ? null : post.getTitle(),
            hidden ? null : post.getBody(),
            authorName(post.getAuthor()),
            blinded, likeCount(postLikes), liked(postLikes), mine,
            post.getCreatedAt(), post.getUpdatedAt(), views);
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
        Post post = posts.findById(postId).orElse(null);
        if (post == null || post.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "글을 찾을 수 없습니다");
        }
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

    // ---------- 응답 만들기 (트랜잭션 안에서. open-in-view가 꺼져 있어 밖에서는 LAZY를 못 읽는다) ----------

    private PostSummary summary(Post post, LikeCount count, Long viewerId) {
        boolean mine = post.isWrittenBy(viewerId);
        boolean blinded = post.getStatus() == PostStatus.BLINDED;
        return new PostSummary(
            post.getId(), post.getTargetId(),
            blinded && !mine ? null : post.getTitle(),
            authorName(post.getAuthor()),
            blinded, likeCount(count), liked(count), mine, post.getCreatedAt());
    }

    private CommentView view(Comment comment, LikeCount count, Long viewerId) {
        boolean mine = comment.isWrittenBy(viewerId);
        boolean blinded = comment.getStatus() == PostStatus.BLINDED;
        return new CommentView(
            comment.getId(),
            blinded && !mine ? null : comment.getBody(),
            authorName(comment.getAuthor()),
            blinded, likeCount(count), liked(count), mine,
            comment.getCreatedAt(), comment.getUpdatedAt());
    }

    // 탈퇴하면 닉네임이 지워진다. "탈퇴"는 닉네임 예약어라 진짜 회원과 헷갈리지 않는다
    private static String authorName(Member author) {
        return author.isWithdrawn() ? WITHDRAWN : author.getNickname();
    }

    // 공감이 하나도 없으면 LikeRepository 결과에 없다 (null)
    private static long likeCount(LikeCount count) {
        return count == null ? 0 : count.likes();
    }

    private static boolean liked(LikeCount count) {
        return count != null && count.mine();
    }

    // ---------- 확인 ----------

    // 글·댓글을 쓰거나 고칠 자격. 회원 행을 잠가 같은 회원의 쓰기 요청을 한 줄로 세운다
    private Member writer(Long memberId) {
        Member member = members.findForUpdate(memberId).orElse(null);
        if (member == null || member.isWithdrawn()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
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
        Member member = members.findById(memberId).orElse(null);
        if (member == null || member.isWithdrawn()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (member.needsNickname()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "닉네임을 먼저 정해 주세요");
        }
        return member;
    }

    private Post ownPost(Long memberId, long postId) {
        Post post = posts.findById(postId).orElse(null);
        if (post == null || post.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "글을 찾을 수 없습니다");
        }
        if (!post.isWrittenBy(memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 글만 고치거나 지울 수 있습니다");
        }
        return post;
    }

    private Comment ownComment(Long memberId, long commentId) {
        Comment comment = comments.findById(commentId).orElse(null);
        if (comment == null || comment.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다");
        }
        if (!comment.isWrittenBy(memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 댓글만 고치거나 지울 수 있습니다");
        }
        return comment;
    }
}
