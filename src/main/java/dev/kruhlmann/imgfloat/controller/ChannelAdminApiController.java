package dev.kruhlmann.imgfloat.controller;

import dev.kruhlmann.imgfloat.model.OauthSessionUser;
import dev.kruhlmann.imgfloat.model.api.response.TwitchUserProfile;
import dev.kruhlmann.imgfloat.model.api.request.AdminRequest;
import dev.kruhlmann.imgfloat.service.AuthorizationService;
import dev.kruhlmann.imgfloat.service.ChannelDirectoryService;
import dev.kruhlmann.imgfloat.service.TwitchUserLookupService;
import dev.kruhlmann.imgfloat.util.LogSanitizer;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AbstractOAuth2Token;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manages channel admin membership (add, remove, list) and Twitch moderator suggestions.
 * Extracted from {@link ChannelApiController} to reduce its surface area.
 */
@RestController
@RequestMapping("/api/channels/{broadcaster}/admins")
@SecurityRequirement(name = "twitchOAuth")
public class ChannelAdminApiController {

    private static final Logger LOG = LoggerFactory.getLogger(ChannelAdminApiController.class);

    private final ChannelDirectoryService channelDirectoryService;
    private final TwitchUserLookupService twitchUserLookupService;
    private final AuthorizationService authorizationService;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final OAuth2AuthorizedClientRepository authorizedClientRepository;

    public ChannelAdminApiController(
        ChannelDirectoryService channelDirectoryService,
        TwitchUserLookupService twitchUserLookupService,
        AuthorizationService authorizationService,
        OAuth2AuthorizedClientService authorizedClientService,
        OAuth2AuthorizedClientRepository authorizedClientRepository
    ) {
        this.channelDirectoryService = channelDirectoryService;
        this.twitchUserLookupService = twitchUserLookupService;
        this.authorizationService = authorizationService;
        this.authorizedClientService = authorizedClientService;
        this.authorizedClientRepository = authorizedClientRepository;
    }

    @PostMapping
    public ResponseEntity<Boolean> addAdmin(
        @PathVariable("broadcaster") String broadcaster,
        @Valid @RequestBody AdminRequest request,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        String logBroadcaster = LogSanitizer.sanitize(broadcaster);
        String logSessionUsername = LogSanitizer.sanitize(sessionUsername);
        String logRequestUsername = LogSanitizer.sanitize(request.username());
        authorizationService.userMatchesSessionUsernameOrThrowHttpError(broadcaster, sessionUsername);
        LOG.info("User {} adding admin {} to {}", logSessionUsername, logRequestUsername, logBroadcaster);
        boolean added = channelDirectoryService.addAdmin(broadcaster, request.username(), sessionUsername);
        if (!added) {
            LOG.info("User {} already admin for {} or could not be added", logRequestUsername, logBroadcaster);
        }
        return ResponseEntity.ok(added);
    }

    @GetMapping
    public Collection<TwitchUserProfile> listAdmins(
        @PathVariable("broadcaster") String broadcaster,
        OAuth2AuthenticationToken oauthToken,
        HttpServletRequest request
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        String logBroadcaster = LogSanitizer.sanitize(broadcaster);
        String logSessionUsername = LogSanitizer.sanitize(sessionUsername);
        authorizationService.userMatchesSessionUsernameOrThrowHttpError(broadcaster, sessionUsername);
        LOG.debug("Listing admins for {} by {}", logBroadcaster, logSessionUsername);
        var channel = channelDirectoryService.getOrCreateChannel(broadcaster);
        List<String> admins = channel.getAdmins().stream().sorted(Comparator.naturalOrder()).toList();
        OAuth2AuthorizedClient authorizedClient = resolveAuthorizedClient(oauthToken, request);
        String accessToken = Optional.ofNullable(authorizedClient)
            .map(OAuth2AuthorizedClient::getAccessToken)
            .map(AbstractOAuth2Token::getTokenValue)
            .orElse(null);
        String clientId = Optional.ofNullable(authorizedClient)
            .map(OAuth2AuthorizedClient::getClientRegistration)
            .map(ClientRegistration::getClientId)
            .orElse(null);
        return twitchUserLookupService.fetchProfiles(admins, accessToken, clientId);
    }

    @GetMapping("/suggestions")
    public Collection<TwitchUserProfile> listAdminSuggestions(
        @PathVariable("broadcaster") String broadcaster,
        OAuth2AuthenticationToken oauthToken,
        HttpServletRequest request
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        String logBroadcaster = LogSanitizer.sanitize(broadcaster);
        String logSessionUsername = LogSanitizer.sanitize(sessionUsername);
        authorizationService.userMatchesSessionUsernameOrThrowHttpError(broadcaster, sessionUsername);
        LOG.debug("Listing admin suggestions for {} by {}", logBroadcaster, logSessionUsername);
        var channel = channelDirectoryService.getOrCreateChannel(broadcaster);
        OAuth2AuthorizedClient authorizedClient = resolveAuthorizedClient(oauthToken, request);

        if (authorizedClient == null) {
            LOG.warn(
                "No authorized Twitch client found for {} while fetching admin suggestions for {}",
                logSessionUsername,
                logBroadcaster
            );
            return List.of();
        }
        String accessToken = Optional.of(authorizedClient)
            .map(OAuth2AuthorizedClient::getAccessToken)
            .map(AbstractOAuth2Token::getTokenValue)
            .orElse(null);
        String clientId = Optional.of(authorizedClient)
            .map(OAuth2AuthorizedClient::getClientRegistration)
            .map(ClientRegistration::getClientId)
            .orElse(null);
        if (accessToken == null || accessToken.isBlank() || clientId == null || clientId.isBlank()) {
            LOG.warn(
                "Missing Twitch credentials for {} while fetching admin suggestions for {}",
                logSessionUsername,
                logBroadcaster
            );
            return List.of();
        }
        return twitchUserLookupService.fetchModerators(broadcaster, channel.getAdmins(), accessToken, clientId);
    }

    @DeleteMapping("/{username}")
    public ResponseEntity<Boolean> removeAdmin(
        @PathVariable("broadcaster") String broadcaster,
        @PathVariable("username") String username,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        String logBroadcaster = LogSanitizer.sanitize(broadcaster);
        String logSessionUsername = LogSanitizer.sanitize(sessionUsername);
        String logUsername = LogSanitizer.sanitize(username);
        authorizationService.userMatchesSessionUsernameOrThrowHttpError(broadcaster, sessionUsername);
        LOG.info("User {} removing admin {} from {}", logSessionUsername, logUsername, logBroadcaster);
        boolean removed = channelDirectoryService.removeAdmin(broadcaster, username, sessionUsername);
        return ResponseEntity.ok(removed);
    }

    private OAuth2AuthorizedClient resolveAuthorizedClient(
        @Nullable OAuth2AuthenticationToken oauthToken,
        HttpServletRequest request
    ) {
        if (oauthToken == null) {
            LOG.error("Attempt to resolve authorized client without oauth token");
            return null;
        }
        OAuth2AuthorizedClient sessionClient = authorizedClientRepository.loadAuthorizedClient(
            oauthToken.getAuthorizedClientRegistrationId(),
            oauthToken,
            request
        );
        if (sessionClient != null) {
            return sessionClient;
        }
        return authorizedClientService.loadAuthorizedClient(
            oauthToken.getAuthorizedClientRegistrationId(),
            oauthToken.getName()
        );
    }
}
