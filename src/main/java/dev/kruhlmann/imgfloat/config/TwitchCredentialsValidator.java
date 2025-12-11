package dev.kruhlmann.imgfloat.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TwitchCredentialsValidator {
    private final String clientId;
    private final String clientSecret;

    public TwitchCredentialsValidator(
            @Value("${spring.security.oauth2.client.registration.twitch.client-id}") String clientId,
            @Value("${spring.security.oauth2.client.registration.twitch.client-secret}") String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @PostConstruct
    void validate() {
        ensurePresent(clientId, "TWITCH_CLIENT_ID");
        ensurePresent(clientSecret, "TWITCH_CLIENT_SECRET");
    }

    private void ensurePresent(String value, String name) {
        if (!StringUtils.hasText(value) || "changeme".equalsIgnoreCase(value.trim())) {
            throw new IllegalStateException(name + " must be set in the environment or .env file");
        }
    }
}
