package io.github.crm08134gif.backend.board;

import io.github.crm08134gif.backend.member.Member;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "comment")
public class Comment implements Moderatable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 댓글 → 글 방향만 둔다(단방향). 글 → 댓글 목록은 저장소에서 따로 묻는다
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private Member author;

    // 제목은 없다 (V12 comment에 title 칸이 없다)
    private String body;

    @Enumerated(EnumType.STRING)
    private PostStatus status;

    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    protected Comment() {}

    public Comment(Post post, Member author, String body, Instant now) {
        this.post = post;
        this.author = author;
        this.body = body;
        this.status = PostStatus.VISIBLE;
        this.createdAt = now;
    }

    public Long getId() { return id; }
    public Post getPost() { return post; }
    public Member getAuthor() { return author; }
    public String getBody() { return body; }
    @Override
    public PostStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @Override
    public boolean isDeleted() {
        return status == PostStatus.DELETED;
    }

    // 프록시라도 getId()는 SELECT 없이 나온다
    @Override
    public boolean isWrittenBy(Long memberId) {
        return author.getId().equals(memberId);
    }

    public void edit(String body, Instant now) {
        this.body = body;
        this.updatedAt = now;
    }

    public void delete(Instant now) {
        this.status = PostStatus.DELETED;
        this.deletedAt = now;
    }

    // 신고 2명이 차거나 관리자가 가린다. 지운 것은 그대로 둔다
    @Override
    public void blind() {
        if (status == PostStatus.VISIBLE) {
            status = PostStatus.BLINDED;
        }
    }

    // 관리자가 검토해서 되살린다
    @Override
    public void restore() {
        if (status == PostStatus.BLINDED) {
            status = PostStatus.VISIBLE;
        }
    }
}
