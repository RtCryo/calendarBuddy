package com.calendarbuddy.api.security;

import com.calendarbuddy.api.account.AccountPrincipal;
import com.calendarbuddy.api.account.AccountResolver;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, Converter<Jwt, AbstractAuthenticationToken> accountConverter)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        requests ->
                                requests.requestMatchers(
                                                "/actuator/health", "/actuator/health/**", "/error")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/actuator/prometheus")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(
                        oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(accountConverter)))
                .exceptionHandling(
                        errors ->
                                errors.authenticationEntryPoint(
                                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${app.auth.jwks-uri}") String jwksUri,
            @Value("${app.auth.issuer}") String issuer,
            @Value("${app.auth.audience}") String audience) {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withJwkSetUri(jwksUri)
                        .jwsAlgorithm(SignatureAlgorithm.RS256)
                        .build();
        // getAudience() is null when the token has no aud claim; that token must fail as invalid,
        // not throw.
        OAuth2TokenValidator<Jwt> audienceValidator =
                jwt ->
                        jwt.getAudience() != null && jwt.getAudience().contains(audience)
                                ? OAuth2TokenValidatorResult.success()
                                : OAuth2TokenValidatorResult.failure(
                                        new OAuth2Error("invalid_token", "Wrong audience", null));
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        new JwtTimestampValidator(Duration.ofSeconds(60)),
                        new JwtIssuerValidator(issuer),
                        audienceValidator));
        return decoder;
    }

    @Bean
    Converter<Jwt, AbstractAuthenticationToken> accountConverter(AccountResolver resolver) {
        return jwt -> {
            AccountPrincipal principal = resolver.resolve(jwt);
            return new UsernamePasswordAuthenticationToken(
                    principal, jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        };
    }
}
