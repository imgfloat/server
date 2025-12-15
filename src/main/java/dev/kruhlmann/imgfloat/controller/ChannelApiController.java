package dev.kruhlmann.imgfloat.controller;

import dev.kruhlmann.imgfloat.model.AdminRequest;
import dev.kruhlmann.imgfloat.model.AssetView;
import dev.kruhlmann.imgfloat.model.CanvasSettingsRequest;
import dev.kruhlmann.imgfloat.model.PlaybackRequest;
import dev.kruhlmann.imgfloat.model.TransformRequest;
import dev.kruhlmann.imgfloat.model.TwitchUserProfile;
import dev.kruhlmann.imgfloat.model.VisibilityRequest;
import dev.kruhlmann.imgfloat.model.OauthSessionUser;
import dev.kruhlmann.imgfloat.service.ChannelDirectoryService;
import dev.kruhlmann.imgfloat.service.TwitchUserLookupService;
import dev.kruhlmann.imgfloat.service.AuthorizationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/channels/{broadcaster}")
@SecurityRequirement(name = "twitchOAuth")
public class ChannelApiController {
    private static final Logger LOG = LoggerFactory.getLogger(ChannelApiController.class);
    private final ChannelDirectoryService channelDirectoryService;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final TwitchUserLookupService twitchUserLookupService;
    private final AuthorizationService authorizationService;

    public ChannelApiController(
        ChannelDirectoryService channelDirectoryService,
        OAuth2AuthorizedClientService authorizedClientService,
        TwitchUserLookupService twitchUserLookupService,
        AuthorizationService authorizationService
    ) {
        this.channelDirectoryService = channelDirectoryService;
        this.authorizedClientService = authorizedClientService;
        this.twitchUserLookupService = twitchUserLookupService;
        this.authorizationService = authorizationService;
    }

    @PostMapping("/admins")
    public ResponseEntity<?> addAdmin(@PathVariable("broadcaster") String broadcaster,
                                      @Valid @RequestBody AdminRequest request,
                                      OAuth2AuthenticationToken oauthToken) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userMatchesSessionUsernameOrThrowHttpError(broadcaster, sessionUsername);
        LOG.info("User {} adding admin {} to {}", sessionUsername, request.getUsername(), broadcaster);
        boolean added = channelDirectoryService.addAdmin(broadcaster, request.getUsername());
        if (!added) {
            LOG.info("User {} already admin for {} or could not be added", request.getUsername(), broadcaster);
        }
        return ResponseEntity.ok().body(added);
    }

    @GetMapping("/admins")
    public Collection<TwitchUserProfile> listAdmins(@PathVariable("broadcaster") String broadcaster,
                                                    OAuth2AuthenticationToken oauthToken,
                                                    @RegisteredOAuth2AuthorizedClient("twitch") OAuth2AuthorizedClient authorizedClient) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userMatchesSessionUsernameOrThrowHttpError(broadcaster, sessionUsername);
        LOG.debug("Listing admins for {} by {}", broadcaster, sessionUsername);
        var channel = channelDirectoryService.getOrCreateChannel(broadcaster);
        List<String> admins = channel.getAdmins().stream()
                .sorted(Comparator.naturalOrder())
                .toList();
        authorizedClient = resolveAuthorizedClient(oauthToken, authorizedClient);
        String accessToken = Optional.ofNullable(authorizedClient)
                .map(OAuth2AuthorizedClient::getAccessToken)
                .map(token -> token.getTokenValue())
                .orElse(null);
        String clientId = Optional.ofNullable(authorizedClient)
                .map(OAuth2AuthorizedClient::getClientRegistration)
                .map(registration -> registration.getClientId())
                .orElse(null);
        return twitchUserLookupService.fetchProfiles(admins, accessToken, clientId);
    }

    @GetMapping("/admins/suggestions")
    public Collection<TwitchUserProfile> listAdminSuggestions(@PathVariable("broadcaster") String broadcaster,
                                                              OAuth2AuthenticationToken oauthToken,
                                                              @RegisteredOAuth2AuthorizedClient("twitch") OAuth2AuthorizedClient authorizedClient) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userMatchesSessionUsernameOrThrowHttpError(broadcaster, sessionUsername);
        LOG.debug("Listing admin suggestions for {} by {}", broadcaster, sessionUsername);
        var channel = channelDirectoryService.getOrCreateChannel(broadcaster);
        authorizedClient = resolveAuthorizedClient(oauthToken, authorizedClient);

        if (authorizedClient == null) {
            LOG.warn("No authorized Twitch client found for {} while fetching admin suggestions for {}", sessionUsername, broadcaster);
            return List.of();
        }
        String accessToken = Optional.ofNullable(authorizedClient)
                .map(OAuth2AuthorizedClient::getAccessToken)
                .map(token -> token.getTokenValue())
                .orElse(null);
        String clientId = Optional.ofNullable(authorizedClient)
                .map(OAuth2AuthorizedClient::getClientRegistration)
                .map(registration -> registration.getClientId())
                .orElse(null);
        if (accessToken == null || accessToken.isBlank() || clientId == null || clientId.isBlank()) {
            LOG.warn("Missing Twitch credentials for {} while fetching admin suggestions for {}", sessionUsername, broadcaster);
            return List.of();
        }
        return twitchUserLookupService.fetchModerators(broadcaster, channel.getAdmins(), accessToken, clientId);
    }

    @DeleteMapping("/admins/{username}")
    public ResponseEntity<?> removeAdmin(@PathVariable("broadcaster") String broadcaster,
                                         @PathVariable("username") String username,
                                        OAuth2AuthenticationToken oauthToken) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userMatchesSessionUsernameOrThrowHttpError(broadcaster, sessionUsername);
        LOG.info("User {} removing admin {} from {}", sessionUsername, username, broadcaster);
        boolean removed = channelDirectoryService.removeAdmin(broadcaster, username);
        return ResponseEntity.ok().body(removed);
    }

    @GetMapping("/assets")
    public Collection<AssetView> listAssets(@PathVariable("broadcaster") String broadcaster) {
        return channelDirectoryService.getAssetsForAdmin(broadcaster);
    }

    @GetMapping("/assets/visible")
    public Collection<AssetView> listVisible(@PathVariable("broadcaster") String broadcaster) {
        return channelDirectoryService.getVisibleAssets(broadcaster);
    }

    @GetMapping("/canvas")
    public CanvasSettingsRequest getCanvas(@PathVariable("broadcaster") String broadcaster) {
        return channelDirectoryService.getCanvasSettings(broadcaster);
    }

    @PutMapping("/canvas")
    public CanvasSettingsRequest updateCanvas(@PathVariable("broadcaster") String broadcaster,
                                              @Valid @RequestBody CanvasSettingsRequest request,
                                              OAuth2AuthenticationToken oauthToken) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userMatchesSessionUsernameOrThrowHttpError(broadcaster, sessionUsername);
        LOG.info("Updating canvas for {} by {}: {}x{}", broadcaster, sessionUsername, request.getWidth(), request.getHeight());
        return channelDirectoryService.updateCanvasSettings(broadcaster, request);
    }

    @PostMapping(value = "/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AssetView> createAsset(@PathVariable("broadcaster") String broadcaster,
                                             @org.springframework.web.bind.annotation.RequestPart("file") MultipartFile file,
                                             OAuth2AuthenticationToken oauthToken) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(broadcaster, sessionUsername);
        if (file == null || file.isEmpty()) {
            LOG.warn("User {} attempted to upload empty file to {}", sessionUsername, broadcaster);
            throw new ResponseStatusException(BAD_REQUEST, "Asset file is required");
        }
        try {
            LOG.info("User {} uploading asset {} to {}", sessionUsername, file.getOriginalFilename(), broadcaster);
            return channelDirectoryService.createAsset(broadcaster, file)
                    .map(ResponseEntity::ok)
                    .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Unable to read image"));
        } catch (IOException e) {
            LOG.error("Failed to process asset upload for {} by {}", broadcaster, sessionUsername, e);
            throw new ResponseStatusException(BAD_REQUEST, "Failed to process image", e);
        }
    }

    @PutMapping("/assets/{assetId}/transform")
    public ResponseEntity<AssetView> transform(@PathVariable("broadcaster") String broadcaster,
                                               @PathVariable("assetId") String assetId,
                                               @Valid @RequestBody TransformRequest request,
                                               OAuth2AuthenticationToken oauthToken) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(broadcaster, sessionUsername);
        LOG.debug("Applying transform to asset {} on {} by {}", assetId, broadcaster, sessionUsername);
        return channelDirectoryService.updateTransform(broadcaster, assetId, request)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> {
                    LOG.warn("Transform request for missing asset {} on {} by {}", assetId, broadcaster, sessionUsername);
                    return new ResponseStatusException(NOT_FOUND, "Asset not found");
                });
    }

    @PostMapping("/assets/{assetId}/play")
    public ResponseEntity<AssetView> play(@PathVariable("broadcaster") String broadcaster,
                                          @PathVariable("assetId") String assetId,
                                          @RequestBody(required = false) PlaybackRequest request,
                                          OAuth2AuthenticationToken oauthToken) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(broadcaster, sessionUsername);
        LOG.info("Triggering playback for asset {} on {} by {}", assetId, broadcaster, sessionUsername);
        return channelDirectoryService.triggerPlayback(broadcaster, assetId, request)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Asset not found"));
    }

    @PutMapping("/assets/{assetId}/visibility")
    public ResponseEntity<AssetView> visibility(@PathVariable("broadcaster") String broadcaster,
                                                @PathVariable("assetId") String assetId,
                                                @RequestBody VisibilityRequest request,
                                                OAuth2AuthenticationToken oauthToken) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(broadcaster, sessionUsername);
        LOG.info("Updating visibility for asset {} on {} by {} to hidden={} ", assetId, broadcaster, sessionUsername , request.isHidden());
        return channelDirectoryService.updateVisibility(broadcaster, assetId, request)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> {
                    LOG.warn("Visibility update for missing asset {} on {} by {}", assetId, broadcaster, sessionUsername);
                    return new ResponseStatusException(NOT_FOUND, "Asset not found");
                });
    }

    @GetMapping("/assets/{assetId}/content")
    public ResponseEntity<byte[]> getAssetContent(@PathVariable("broadcaster") String broadcaster,
                                                  @PathVariable("assetId") String assetId) {
        LOG.debug("Serving asset {} for broadcaster {}", assetId, broadcaster);
        return channelDirectoryService.getAssetContent(assetId)
            .map(content -> ResponseEntity.ok()
                    .header("X-Content-Type-Options", "nosniff")
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDispositionFor(content.mediaType()))
                    .contentType(MediaType.parseMediaType(content.mediaType()))
                    .body(content.bytes()))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Asset not found"));
    }

    @GetMapping("/assets/{assetId}/preview")
    public ResponseEntity<byte[]> getAssetPreview(@PathVariable("broadcaster") String broadcaster,
                                                  @PathVariable("assetId") String assetId) {
        LOG.debug("Serving preview for asset {} for broadcaster {}", assetId, broadcaster);
        return channelDirectoryService.getAssetPreview(assetId, true)
            .map(content -> ResponseEntity.ok()
                    .header("X-Content-Type-Options", "nosniff")
                    .contentType(MediaType.parseMediaType(content.mediaType()))
                    .body(content.bytes()))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Preview not found"));
    }

    private String contentDispositionFor(String mediaType) {
        if (mediaType != null && dev.kruhlmann.imgfloat.service.media.MediaDetectionService.isInlineDisplayType(mediaType)) {
            return "inline";
        }
        return "attachment";
    }

    @DeleteMapping("/assets/{assetId}")
    public ResponseEntity<?> delete(@PathVariable("broadcaster") String broadcaster,
                                    @PathVariable("assetId") String assetId,
                                    OAuth2AuthenticationToken oauthToken) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(broadcaster, sessionUsername);
        boolean removed = channelDirectoryService.deleteAsset(assetId);
        if (!removed) {
            LOG.warn("Attempt to delete missing asset {} on {} by {}", assetId, broadcaster, sessionUsername);
            throw new ResponseStatusException(NOT_FOUND, "Asset not found");
        }
        LOG.info("Asset {} deleted on {} by {}", assetId, broadcaster, sessionUsername);
        return ResponseEntity.ok().build();
    }

    private OAuth2AuthorizedClient resolveAuthorizedClient(OAuth2AuthenticationToken oauthToken,
                                                           OAuth2AuthorizedClient authorizedClient) {
        if (authorizedClient != null) {
            return authorizedClient;
        }
        if (oauthToken == null) {
            return null;
        }
        return authorizedClientService.loadAuthorizedClient(oauthToken.getAuthorizedClientRegistrationId(), oauthToken.getName());
    }
}
