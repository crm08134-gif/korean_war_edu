package io.github.crm08134gif.backend.member;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "member")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String provider;
    private String providerUserId;
    private String nickname;
    private Instant nicknameChangedAt;

    // DB에는 'USER', 'ADMIN' 글자로 저장한다 (순서 번호로 저장하면 enum 순서를 바꿀 때 데이터가 틀어진다)
    @Enumerated(EnumType.STRING)
    private Role role;

    private Instant writeBannedUntil;
    private Instant createdAt;
    private Instant withdrawnAt;

    // 닉네임을 정하면 30일 동안 바꿀 수 없다 (제재받은 사람이 닉네임만 바꿔 딴사람인 척하는 것을 막는다)
    private static final Duration NICKNAME_CHANGE_INTERVAL = Duration.ofDays(30);

    // JPA가 DB에서 읽은 행으로 객체를 만들 때 쓴다. 우리 코드는 쓰지 않으므로 protected
    protected Member() {}

    // 첫 로그인 때 만든다. 닉네임은 아직 없다
    public Member(String provider, String providerUserId) {
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.role = Role.USER;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getNickname() {
        return nickname;
    }

    public Role getRole() {
        return role;
    }

    public boolean isWithdrawn() {
        return withdrawnAt != null;
    }

    public boolean needsNickname() {
        return nickname == null;
    }

    // 처음 정할 때는 바로, 바꿀 때는 마지막으로 정한 뒤 30일이 지나야
    public boolean canChangeNickname(Instant now) {
        return nickname == null || nicknameChangedAt.plus(NICKNAME_CHANGE_INTERVAL).isBefore(now);
    }

    // 필드만 바꾼다. UPDATE 문은 트랜잭션이 끝날 때 Hibernate가 보낸다 (변경 감지)
    public void changeNickname(String nickname, Instant now) {
        this.nickname = nickname;
        this.nicknameChangedAt = now;
    }
}
