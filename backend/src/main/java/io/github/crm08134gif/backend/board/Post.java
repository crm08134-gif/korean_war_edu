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
@Table(name = "post")

public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private BoardType board;

    private String targetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private Member author;

    private String title;
    private String body;

    @Enumerated(EnumType.STRING)
    private PostStatus status;

    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    protected Post() {}

    public Post(BoardType board, String targetId, Member author, String title, String body, Instant now) {
        this.board = board;
        this.targetId = targetId;
        this.author = author;
        this.title = title;
        this.body = body;
        this.status = PostStatus.VISIBLE;
        this.createdAt = now;
    }

    public Long getId() { return id; }
    public BoardType getBoard() { return board; }
    public String getTargetId() { return targetId; }
    public Member getAuthor() { return author; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public PostStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public boolean isDeleted() {
        return status == PostStatus.DELETED;
    }

    public boolean isWrittenBy(Long memberId) {
        return author.getId().equals(memberId);
    }

    public void edit(String title, String body, Instant now) {
        this.title = title;
        this.body = body;
        this.updatedAt = now;
    }

    public void delete(Instant now) {
        this.status = PostStatus.DELETED;
        this.deletedAt = now;
    }
}
