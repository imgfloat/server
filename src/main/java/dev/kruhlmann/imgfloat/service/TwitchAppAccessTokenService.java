package dev.kruhlmann.imgfloat.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class TwitchAppAccessTokenService {

    private static final Logger LOG = LoggerFactory.getLogger(TwitchAppAccessTokenService.class);
    private final RestTemplate restTemplate;
    private final String clientId;
    private final String clientSecret;
    private volatile AccessToken cachedToken;

    public TwitchAppAccessTokenService(
        RestTemplateBuilder builder,
        @Value("${spring.security.oauth2.client.registration.twitch.client-id:#{null}}") String clientId,
        @Value("${spring.security.oauth2.client.registration.twitch.client-secret:#{null}}") String clientSecret
    ) {
        this.restTemplate = builder
            .setConnectTimeout(Duration.ofSeconds(15))
            .setReadTimeout(Duration.ofSeconds(15))
            .build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public Optional<String> getAccessToken() {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            return Optional.empty();
        }
        AccessToken current = cachedToken;
        if (current != null && current.isActive()) {
            return Optional.of(current.token());
        }
        synchronized (this) {
            AccessToken refreshed = cachedToken;
            if (refreshed != null && refreshed.isActive()) {
                return Optional.of(refreshed.token());
            }
            cachedToken = requestToken();
            return Optional.ofNullable(cachedToken).map(AccessToken::token);
        }
    }

    public Optional<String> getClientId() {
        if (clientId == null || clientId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(clientId);
    }

    private AccessToken requestToken() {
        if (clientId == null || clientSecret == null) {
            return null;
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl("https://id.twitch.tv/oauth2/token")
            .queryParam("client_id", clientId)
            .queryParam("client_secret", clientSecret)
            .queryParam("grant_type", "client_credentials");

        try {
            ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                builder.build(true).toUri(),
                null,
                TokenResponse.class
            );
            TokenResponse body = response.getBody();
            if (body == null || body.accessToken() == null || body.accessToken().isBlank()) {
                LOG.warn("Unable to fetch Twitch app token: empty response");
                return null;
            }
            Instant expiresAt = Instant.now().plusSeconds(Math.max(0, body.expiresIn() - 60));
            return new AccessToken(body.accessToken(), expiresAt);
        } catch (RestClientException ex) {
            LOG.warn("Unable to fetch Twitch app token", ex);
            return null;
        }
    }

    private record AccessToken(String token, Instant expiresAt) {
        boolean isActive() {
            return expiresAt != null && !expiresAt.isBefore(Instant.now());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(@JsonProperty("access_token") String accessToken, @JsonProperty("expires_in") long expiresIn) {}
}
