package com.calendarbuddy.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

class HomeBuddyJwtDecoderTest {

    @Test
    void acceptsHomeBuddyRsaTokensAndRefreshesJwksForNewKey() throws Exception {
        RSAKey first = key("first");
        RSAKey second = key("second");
        AtomicReference<JWKSet> published = new AtomicReference<>(new JWKSet(first.toPublicJWK()));
        HttpServer server = server(published);
        try {
            JwtDecoder decoder = decoder(server);
            assertThat(decoder.decode(token(first, "homebuddy-auth", "calendarbuddy")).getSubject())
                    .isNotBlank();
            published.set(new JWKSet(List.of(first.toPublicJWK(), second.toPublicJWK())));
            assertThat(
                            decoder.decode(token(second, "homebuddy-auth", "calendarbuddy"))
                                    .getSubject())
                    .isNotBlank();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsWrongIssuerAudienceAndLegacyHmac() throws Exception {
        RSAKey key = key("current");
        HttpServer server = server(new AtomicReference<>(new JWKSet(key.toPublicJWK())));
        try {
            JwtDecoder decoder = decoder(server);
            assertThatThrownBy(() -> decoder.decode(token(key, "other", "calendarbuddy")))
                    .isInstanceOf(Exception.class);
            assertThatThrownBy(() -> decoder.decode(token(key, "homebuddy-auth", "finanzbuddy")))
                    .isInstanceOf(Exception.class);
            assertThatThrownBy(
                            () ->
                                    decoder.decode(
                                            token(
                                                    key("unknown"),
                                                    "homebuddy-auth",
                                                    "calendarbuddy")))
                    .isInstanceOf(Exception.class);
            assertThatThrownBy(() -> decoder.decode(expiredToken(key)))
                    .isInstanceOf(Exception.class);
            assertThatThrownBy(() -> decoder.decode(hmacToken())).isInstanceOf(Exception.class);
            assertThatThrownBy(() -> decoder.decode(token(key, "homebuddy-auth", null)))
                    .isInstanceOf(JwtValidationException.class);
        } finally {
            server.stop(0);
        }
    }

    private RSAKey key(String kid) throws Exception {
        return new RSAKeyGenerator(2048).keyID(kid).generate();
    }

    private JwtDecoder decoder(HttpServer server) {
        return new SecurityConfig()
                .jwtDecoder(
                        "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks",
                        "homebuddy-auth",
                        "calendarbuddy");
    }

    private HttpServer server(AtomicReference<JWKSet> published) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/jwks",
                exchange -> {
                    byte[] body = published.get().toString().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    try (var output = exchange.getResponseBody()) {
                        output.write(body);
                    }
                });
        server.start();
        return server;
    }

    private String token(RSAKey key, String issuer, String audience) throws Exception {
        SignedJWT jwt =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                        claims(issuer, audience));
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private String hmacToken() throws Exception {
        SignedJWT jwt =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.HS256).keyID("old").build(),
                        claims("homebuddy-auth", "calendarbuddy"));
        jwt.sign(new MACSigner(new byte[32]));
        return jwt.serialize();
    }

    private String expiredToken(RSAKey key) throws Exception {
        SignedJWT jwt =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                        new JWTClaimsSet.Builder()
                                .issuer("homebuddy-auth")
                                .subject(UUID.randomUUID().toString())
                                .audience("calendarbuddy")
                                .issueTime(Date.from(Instant.now().minusSeconds(3600)))
                                .expirationTime(Date.from(Instant.now().minusSeconds(120)))
                                .claim("email", "person@example.com")
                                .build());
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private JWTClaimsSet claims(String issuer, String audience) {
        return new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(UUID.randomUUID().toString())
                .audience(audience)
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                .claim("email", "person@example.com")
                .build();
    }
}
