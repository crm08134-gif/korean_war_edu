package io.github.crm08134gif.backend.auth;

import io.github.crm08134gif.backend.member.Member;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 토큰 여러 개 → 회원 하나. LAZY: 토큰을 읽을 때 회원은 아직 읽지 않고, member를 실제로 쓸 때 읽는다
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id")
    private Member member;

    private UUID familyId;
    private String tokenHash;
    private String userAgent;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant revokedAt;

    protected RefreshToken() {}

    public RefreshToken(Member member, UUID familyId, String tokenHash, String userAgent, Instant expiresAt) {
        this.member = member;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.userAgent = userAgent;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public Member getMember() {
        return member;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public void revoke(Instant now) {
        this.revokedAt = now;
    }
}
