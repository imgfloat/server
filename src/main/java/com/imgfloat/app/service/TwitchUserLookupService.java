package com.imgfloat.app.service;

import com.imgfloat.app.model.TwitchUserProfile;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TwitchUserLookupService {
    private static final Logger LOG = LoggerFactory.getLogger(TwitchUserLookupService.class);
    private final RestTemplate restTemplate;

    public TwitchUserLookupService(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(15))
                .setReadTimeout(Duration.ofSeconds(15))
                .build();
    }

    public List<TwitchUserProfile> fetchProfiles(Collection<String> logins, String accessToken, String clientId) {
        if (logins == null || logins.isEmpty()) {
            return List.of();
        }

        List<String> normalizedLogins = logins.stream()
                .filter(Objects::nonNull)
                .map(login -> login.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();

        if (accessToken == null || accessToken.isBlank() || clientId == null || clientId.isBlank()) {
            return normalizedLogins.stream()
                    .map(login -> new TwitchUserProfile(login, login, null))
                    .toList();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.add("Client-ID", clientId);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder
                .fromHttpUrl("https://api.twitch.tv/helix/users");
        normalizedLogins.forEach(login -> uriBuilder.queryParam("login", login));

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<TwitchUsersResponse> response = restTemplate.exchange(
                    uriBuilder.build(true).toUri(),
                    HttpMethod.GET,
                    entity,
                    TwitchUsersResponse.class);

            Map<String, TwitchUserData> byLogin = response.getBody() == null
                    ? Collections.emptyMap()
                    : response.getBody().data().stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(
                            user -> user.login().toLowerCase(Locale.ROOT),
                            Function.identity(),
                            (a, b) -> a));

            return normalizedLogins.stream()
                    .map(login -> toProfile(login, byLogin.get(login)))
                    .toList();
        } catch (RestClientException ex) {
            LOG.warn("Unable to fetch Twitch user profiles", ex);
            return normalizedLogins.stream()
                    .map(login -> new TwitchUserProfile(login, login, null))
                    .toList();
        }
    }

    private TwitchUserProfile toProfile(String login, TwitchUserData data) {
        if (data == null) {
            return new TwitchUserProfile(login, login, null);
        }
        return new TwitchUserProfile(login, data.displayName(), data.profileImageUrl());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TwitchUsersResponse(List<TwitchUserData> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TwitchUserData(
            String login,
            @JsonProperty("display_name") String displayName,
            @JsonProperty("profile_image_url") String profileImageUrl) {
    }
}
