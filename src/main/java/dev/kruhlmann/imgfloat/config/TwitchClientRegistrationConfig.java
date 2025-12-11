package dev.kruhlmann.imgfloat.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/**
 * Sanitizes Twitch client registration values (especially whitespace or quoted secrets) before
 * wiring them into Spring Security.
 */
@Configuration
@EnableConfigurationProperties(OAuth2ClientProperties.class)
class TwitchClientRegistrationConfig {
    private static final Logger LOG = LoggerFactory.getLogger(TwitchClientRegistrationConfig.class);

    @Bean
    @Primary
    ClientRegistrationRepository twitchClientRegistrationRepository(OAuth2ClientProperties properties) {
        List<ClientRegistration> registrations = new ArrayList<>();
        for (Map.Entry<String, OAuth2ClientProperties.Registration> entry : properties.getRegistration().entrySet()) {
            String registrationId = entry.getKey();
            OAuth2ClientProperties.Registration registration = entry.getValue();
            String providerId = registration.getProvider() != null ? registration.getProvider() : registrationId;
            OAuth2ClientProperties.Provider provider = properties.getProvider().get(providerId);
            if (provider == null) {
                throw new IllegalStateException(
                        "Missing OAuth2 provider configuration for registration '" + registrationId + "'.");
            }
            if (!"twitch".equals(registrationId)) {
                LOG.warn("Unexpected OAuth2 registration '{}' found; only Twitch is supported.", registrationId);
                continue;
            }
            registrations.add(buildTwitchRegistration(registrationId, registration, provider));
        }
        return new InMemoryClientRegistrationRepository(registrations);
    }

    private ClientRegistration buildTwitchRegistration(
            String registrationId,
            OAuth2ClientProperties.Registration registration,
            OAuth2ClientProperties.Provider provider) {
        String clientId = sanitize(registration.getClientId(), "TWITCH_CLIENT_ID");
        String clientSecret = sanitize(registration.getClientSecret(), "TWITCH_CLIENT_SECRET");
        return ClientRegistration.withRegistrationId(registrationId)
                .clientName(registration.getClientName())
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(new AuthorizationGrantType(registration.getAuthorizationGrantType()))
                .redirectUri(registration.getRedirectUri())
                .scope(registration.getScope())
                .authorizationUri(provider.getAuthorizationUri())
                .tokenUri(provider.getTokenUri())
                .userInfoUri(provider.getUserInfoUri())
                .userNameAttributeName(provider.getUserNameAttribute())
                .build();
    }

    private String sanitize(String value, String name) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            String unquoted = trimmed.substring(1, trimmed.length() - 1).trim();
            LOG.info("Sanitizing {} by stripping surrounding quotes.", name);
            return unquoted;
        }
        return trimmed;
    }
}
