package io.github.crm08134gif.backend.auth;

import io.github.crm08134gif.backend.member.Member;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenService {

    static final Duration ACCESS_TOKEN_LIFETIME = Duration.ofMinutes(15);
    static final Duration REFRESH_TOKEN_LIFETIME = Duration.ofDays(14);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JwtEncoder jwtEncoder;
    private final RefreshTokenRepository refreshTokens;

    TokenService(JwtEncoder jwtEncoder, RefreshTokenRepository refreshTokens) {
        this.jwtEncoder = jwtEncoder;
        this.refreshTokens = refreshTokens;
    }

    // 새로 로그인한 기기에는 새 묶음(family)을 만든다
    @Transactional
    public IssuedTokens login(Member member, String userAgent) {
        return issue(member, UUID.randomUUID(), userAgent);
    }

    // 리프레시 토큰으로 새 토큰 두 개를 받는다. 쓴 토큰은 폐기한다 (rotation)
    // 실패를 예외로 던지지 않고 빈 값으로 돌려주는 이유: 예외면 아래의 "묶음 폐기"까지 롤백된다
    @Transactional
    public Optional<IssuedTokens> refresh(String rawRefreshToken, String userAgent) {
        Instant now = Instant.now();
        Optional<RefreshToken> found = refreshTokens.findByTokenHash(hash(rawRefreshToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        RefreshToken token = found.get();
        if (token.isRevoked()) {
            // 이미 쓴 토큰이 또 왔다 = 누군가 훔쳐 썼을 수 있다. 이 기기의 로그인을 모두 끊는다
            refreshTokens.revokeFamily(token.getFamilyId(), now);
            return Optional.empty();
        }
        // 지연 로딩: 여기서 처음 member를 쓰므로 이때 회원 SELECT가 나간다
        Member member = token.getMember();
        if (token.isExpired(now) || member.isWithdrawn()) {
            return Optional.empty();
        }
        token.revoke(now);
        return Optional.of(issue(member, token.getFamilyId(), userAgent));
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokens.findByTokenHash(hash(rawRefreshToken))
            .ifPresent(token -> refreshTokens.revokeFamily(token.getFamilyId(), Instant.now()));
    }

    private IssuedTokens issue(Member member, UUID familyId, String userAgent) {
        Instant now = Instant.now();
        String raw = randomToken();
        refreshTokens.save(new RefreshToken(member, familyId, hash(raw), shorten(userAgent),
            now.plus(REFRESH_TOKEN_LIFETIME)));
        return new IssuedTokens(accessToken(member, now), raw);
    }

    // 액세스 토큰(JWT): 회원 번호와 권한만 넣는다. 닉네임 같은 개인정보는 넣지 않는다 (내용은 누구나 풀어 볼 수 있다)
    private String accessToken(Member member, Instant now) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(JwtConfig.ISSUER)
            .audience(List.of("api"))
            .subject(String.valueOf(member.getId()))
            .claim("role", member.getRole().name())
            .issuedAt(now)
            .expiresAt(now.plus(ACCESS_TOKEN_LIFETIME))
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    // DB에는 원문 대신 해시만 둔다. DB가 새어도 쿠키에 넣을 수 있는 값이 없다
    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-256은 모든 자바에 들어 있다
        }
    }

    // 추측할 수 없는 32바이트 난수
    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String shorten(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() > 200 ? userAgent.substring(0, 200) : userAgent;
    }
}




