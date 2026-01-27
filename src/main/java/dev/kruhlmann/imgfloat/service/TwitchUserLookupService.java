package dev.kruhlmann.imgfloat.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.kruhlmann.imgfloat.model.api.response.TwitchUserProfile;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
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

        List<String> normalizedLogins = logins
            .stream()
            .filter(Objects::nonNull)
            .map((login) -> login.toLowerCase(Locale.ROOT))
            .distinct()
            .toList();

        Map<String, TwitchUserData> byLogin = fetchUsers(normalizedLogins, accessToken, clientId);

        return normalizedLogins
            .stream()
            .map((login) -> toProfile(login, byLogin.get(login)))
            .toList();
    }

    public List<TwitchUserProfile> fetchModerators(
        String broadcasterLogin,
        Collection<String> existingAdmins,
        String accessToken,
        String clientId
    ) {
        if (broadcasterLogin == null || broadcasterLogin.isBlank()) {
            LOG.warn("Cannot fetch moderators without a broadcaster login");
            return List.of();
        }

        if (accessToken == null || accessToken.isBlank() || clientId == null || clientId.isBlank()) {
            LOG.warn("Missing Twitch auth details when requesting moderators for {}", broadcasterLogin);
            return List.of();
        }

        String normalizedBroadcaster = broadcasterLogin.toLowerCase(Locale.ROOT);
        Map<String, TwitchUserData> broadcasterData = fetchUsers(List.of(normalizedBroadcaster), accessToken, clientId);
        String broadcasterId = Optional.ofNullable(broadcasterData.get(normalizedBroadcaster))
            .map(TwitchUserData::id)
            .orElse(null);

        if (broadcasterId == null || broadcasterId.isBlank()) {
            LOG.warn("No broadcaster id found for {} when fetching moderators", broadcasterLogin);
            return List.of();
        }

        Set<String> skipLogins = new HashSet<>();
        if (existingAdmins != null) {
            existingAdmins
                .stream()
                .filter(Objects::nonNull)
                .map((login) -> login.toLowerCase(Locale.ROOT))
                .forEach(skipLogins::add);
        }
        skipLogins.add(normalizedBroadcaster);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.add("Client-ID", clientId);

        List<String> moderatorLogins = new ArrayList<>();
        String cursor = null;

        do {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(
                "https://api.twitch.tv/helix/moderation/moderators"
            )
                .queryParam("broadcaster_id", broadcasterId)
                .queryParam("first", 100);
            if (cursor != null && !cursor.isBlank()) {
                builder.queryParam("after", cursor);
            }

            try {
                ResponseEntity<TwitchModeratorsResponse> response = restTemplate.exchange(
                    builder.build(true).toUri(),
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    TwitchModeratorsResponse.class
                );

                TwitchModeratorsResponse body = response.getBody();
                LOG.debug(
                    "Fetched {} moderator records for {} (cursor={})",
                    body != null && body.data() != null ? body.data().size() : 0,
                    broadcasterLogin,
                    cursor
                );
                if (body != null && body.data() != null) {
                    body
                        .data()
                        .stream()
                        .filter(Objects::nonNull)
                        .map(ModeratorData::userLogin)
                        .filter(Objects::nonNull)
                        .map((login) -> login.toLowerCase(Locale.ROOT))
                        .filter((login) -> !skipLogins.contains(login))
                        .forEach(moderatorLogins::add);
                }

                cursor = body != null && body.pagination() != null ? body.pagination().cursor() : null;
            } catch (RestClientException ex) {
                LOG.warn("Unable to fetch Twitch moderators for {}", broadcasterLogin, ex);
                return List.of();
            }
        } while (cursor != null && !cursor.isBlank());

        if (moderatorLogins.isEmpty()) {
            LOG.info("No moderator suggestions available for {} after filtering existing admins", broadcasterLogin);
            return List.of();
        }

        return fetchProfiles(moderatorLogins, accessToken, clientId);
    }

    private TwitchUserProfile toProfile(String login, TwitchUserData data) {
        if (data == null) {
            return new TwitchUserProfile(login, login, null);
        }
        return new TwitchUserProfile(login, data.displayName(), data.profileImageUrl());
    }

    private Map<String, TwitchUserData> fetchUsers(Collection<String> logins, String accessToken, String clientId) {
        if (logins == null || logins.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> normalizedLogins = logins
            .stream()
            .filter(Objects::nonNull)
            .map((login) -> login.toLowerCase(Locale.ROOT))
            .distinct()
            .toList();

        if (accessToken == null || accessToken.isBlank() || clientId == null || clientId.isBlank()) {
            return Collections.emptyMap();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.add("Client-ID", clientId);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl("https://api.twitch.tv/helix/users");
        normalizedLogins.forEach((login) -> uriBuilder.queryParam("login", login));

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<TwitchUsersResponse> response = restTemplate.exchange(
                uriBuilder.build(true).toUri(),
                HttpMethod.GET,
                entity,
                TwitchUsersResponse.class
            );

            return response.getBody() == null
                ? Collections.emptyMap()
                : response
                      .getBody()
                      .data()
                      .stream()
                      .filter(Objects::nonNull)
                      .collect(
                          Collectors.toMap(
                              (user) -> user.login().toLowerCase(Locale.ROOT),
                              Function.identity(),
                              (a, b) -> a,
                              HashMap::new
                          )
                      );
        } catch (RestClientException ex) {
            LOG.warn("Unable to fetch Twitch user profiles", ex);
            return Collections.emptyMap();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TwitchUsersResponse(List<TwitchUserData> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TwitchUserData(
        String id,
        String login,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("profile_image_url") String profileImageUrl
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TwitchModeratorsResponse(List<ModeratorData> data, Pagination pagination) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ModeratorData(
        @JsonProperty("user_id") String userId,
        @JsonProperty("user_login") String userLogin,
        @JsonProperty("user_name") String userName
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Pagination(String cursor) {}
}
