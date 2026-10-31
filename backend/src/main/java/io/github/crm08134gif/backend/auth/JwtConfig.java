package io.github.crm08134gif.backend.auth;

import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
class JwtConfig {

    static final String ISSUER = "korean-war";

    // .env의 JWT_SECRET(Base64)을 서명 키로 바꾼다.
    // 32바이트(256비트)보다 짧은 키는 아래 NimbusJwtEncoder.withSecretKey가 앱 시작 때 거부한다
    @Bean
    SecretKey jwtSecretKey(@Value("${auth.jwt-secret}") String base64Secret) {
        return new SecretKeySpec(Base64.getDecoder().decode(base64Secret), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
    }

    // 액세스 토큰 검사기. Spring Security가 요청마다 이걸로 쿠키의 토큰을 검사한다
    @Bean
    JwtDecoder jwtDecoder(SecretKey key) {
        return decoder(key, "api");
    }

    // 서명, 만료, 발급자, 용도(aud)를 모두 확인한다. 용도가 다른 토큰(로그인 도중 쿠키)을 액세스 토큰으로 쓰지 못하게
    static NimbusJwtDecoder decoder(SecretKey key, String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(ISSUER),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud != null && aud.contains(audience))));
        return decoder;
    }
}
