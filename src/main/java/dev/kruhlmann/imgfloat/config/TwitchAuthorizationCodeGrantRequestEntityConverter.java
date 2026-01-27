package dev.kruhlmann.imgfloat.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequestEntityConverter;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Ensures Twitch token requests always include {@code client_id} and {@code client_secret} in the
 * request body. Twitch ignores HTTP Basic authentication and responds with "missing client id" if
 * those parameters are absent.
 */
final class TwitchAuthorizationCodeGrantRequestEntityConverter
    implements Converter<OAuth2AuthorizationCodeGrantRequest, RequestEntity<?>> {

    private final Converter<OAuth2AuthorizationCodeGrantRequest, RequestEntity<?>> delegate =
        new OAuth2AuthorizationCodeGrantRequestEntityConverter();

    @Override
    public @Nullable RequestEntity<?> convert(@Nullable OAuth2AuthorizationCodeGrantRequest request) {
        assert request != null;
        RequestEntity<?> entity = delegate.convert(request);
        if (entity == null || !(entity.getBody() instanceof MultiValueMap<?, ?> existingBody)) {
            return entity;
        }

        ClientRegistration registration = request.getClientRegistration();

        MultiValueMap<String, String> body = cloneBody(existingBody);
        body.set(OAuth2ParameterNames.CLIENT_ID, registration.getClientId());
        if (registration.getClientSecret() != null) {
            body.set(OAuth2ParameterNames.CLIENT_SECRET, registration.getClientSecret());
        }

        return new RequestEntity<>(
            body,
            entity.getHeaders(),
            entity.getMethod() == null ? HttpMethod.POST : entity.getMethod(),
            entity.getUrl() == null ? URI.create(registration.getProviderDetails().getTokenUri()) : entity.getUrl()
        );
    }

    private MultiValueMap<String, String> cloneBody(MultiValueMap<?, ?> existingBody) {
        MultiValueMap<String, String> copy = new LinkedMultiValueMap<>();
        existingBody.forEach((key, value) -> copy.put(String.valueOf(key), new ArrayList<>((List<String>) value)));
        return copy;
    }
}
