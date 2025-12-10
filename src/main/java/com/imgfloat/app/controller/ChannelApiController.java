package com.imgfloat.app.controller;

import com.imgfloat.app.model.AdminRequest;
import com.imgfloat.app.model.AssetView;
import com.imgfloat.app.model.CanvasSettingsRequest;
import com.imgfloat.app.model.PlaybackRequest;
import com.imgfloat.app.model.TransformRequest;
import com.imgfloat.app.model.TwitchUserProfile;
import com.imgfloat.app.model.VisibilityRequest;
import com.imgfloat.app.service.ChannelDirectoryService;
import com.imgfloat.app.service.TwitchUserLookupService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
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

    public ChannelApiController(ChannelDirectoryService channelDirectoryService,
                               OAuth2AuthorizedClientService authorizedClientService,
                               TwitchUserLookupService twitchUserLookupService) {
        this.channelDirectoryService = channelDirectoryService;
        this.authorizedClientService = authorizedClientService;
        this.twitchUserLookupService = twitchUserLookupService;
    }

    @PostMapping("/admins")
    public ResponseEntity<?> addAdmin(@PathVariable("broadcaster") String broadcaster,
                                      @Valid @RequestBody AdminRequest request,
                                      OAuth2AuthenticationToken authentication) {
        String login = TwitchUser.from(authentication).login();
        ensureBroadcaster(broadcaster, login);
        LOG.info("User {} adding admin {} to {}", login, request.getUsername(), broadcaster);
        boolean added = channelDirectoryService.addAdmin(broadcaster, request.getUsername());
        if (!added) {
            LOG.info("User {} already admin for {} or could not be added", request.getUsername(), broadcaster);
        }
        return ResponseEntity.ok().body(added);
    }

    @GetMapping("/admins")
    public Collection<TwitchUserProfile> listAdmins(@PathVariable("broadcaster") String broadcaster,
                                                    OAuth2AuthenticationToken authentication) {
        String login = TwitchUser.from(authentication).login();
        ensureBroadcaster(broadcaster, login);
        LOG.debug("Listing admins for {} by {}", broadcaster, login);
        var channel = channelDirectoryService.getOrCreateChannel(broadcaster);
        List<String> admins = channel.getAdmins().stream()
                .sorted(Comparator.naturalOrder())
                .toList();
        OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getName());
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
                                                              OAuth2AuthenticationToken authentication) {
        String login = TwitchUser.from(authentication).login();
        ensureBroadcaster(broadcaster, login);
        LOG.debug("Listing admin suggestions for {} by {}", broadcaster, login);
        var channel = channelDirectoryService.getOrCreateChannel(broadcaster);
        OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getName());
        String accessToken = Optional.ofNullable(authorizedClient)
                .map(OAuth2AuthorizedClient::getAccessToken)
                .map(token -> token.getTokenValue())
                .orElse(null);
        String clientId = Optional.ofNullable(authorizedClient)
                .map(OAuth2AuthorizedClient::getClientRegistration)
                .map(registration -> registration.getClientId())
                .orElse(null);
        return twitchUserLookupService.fetchModerators(broadcaster, channel.getAdmins(), accessToken, clientId);
    }

    @DeleteMapping("/admins/{username}")
    public ResponseEntity<?> removeAdmin(@PathVariable("broadcaster") String broadcaster,
                                         @PathVariable("username") String username,
                                         OAuth2AuthenticationToken authentication) {
        String login = TwitchUser.from(authentication).login();
        ensureBroadcaster(broadcaster, login);
        LOG.info("User {} removing admin {} from {}", login, username, broadcaster);
        boolean removed = channelDirectoryService.removeAdmin(broadcaster, username);
        return ResponseEntity.ok().body(removed);
    }

    @GetMapping("/assets")
    public Collection<AssetView> listAssets(@PathVariable("broadcaster") String broadcaster,
                                            OAuth2AuthenticationToken authentication) {
        String login = TwitchUser.from(authentication).login();
        if (!channelDirectoryService.isBroadcaster(broadcaster, login)
                && !channelDirectoryService.isAdmin(broadcaster, login)) {
            LOG.warn("Unauthorized asset listing attempt for {} by {}", broadcaster, login);
            throw new ResponseStatusException(FORBIDDEN, "Not authorized");
        }
        LOG.info("Listing assets for {} requested by {}", broadcaster, login);
        return channelDirectoryService.getAssetsForAdmin(broadcaster);
    }

    @GetMapping("/assets/visible")
    public Collection<AssetView> listVisible(@PathVariable("broadcaster") String broadcaster) {
        return channelDirectoryService.getVisibleAssets(broadcaster);
    }

    @GetMapping("/canvas")
    public CanvasSettingsRequest getCanvas(@PathVariable("broadcaster") String broadcaster) {
        LOG.debug("Fetching canvas settings for {}", broadcaster);
        return channelDirectoryService.getCanvasSettings(broadcaster);
    }

    @PutMapping("/canvas")
    public CanvasSettingsRequest updateCanvas(@PathVariable("broadcaster") String broadcaster,
                                              @Valid @RequestBody CanvasSettingsRequest request,
                                              OAuth2AuthenticationToken authentication) {
        String login = TwitchUser.from(authentication).login();
        ensureBroadcaster(broadcaster, login);
        LOG.info("Updating canvas for {} by {}: {}x{}", broadcaster, login, request.getWidth(), request.getHeight());
        return channelDirectoryService.updateCanvasSettings(broadcaster, request);
    }

    @PostMapping(value = "/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AssetView> createAsset(@PathVariable("broadcaster") String broadcaster,
                                             @org.springframework.web.bind.annotation.RequestPart("file") MultipartFile file,
                                             OAuth2AuthenticationToken authentication) {
        String login = TwitchUser.from(authentication).login();
        ensureAuthorized(broadcaster, login);
        if (file == null || file.isEmpty()) {
            LOG.warn("User {} attempted to upload empty file to {}", login, broadcaster);
            throw new ResponseStatusException(BAD_REQUEST, "Asset file is required");
        }
        try {
            LOG.info("User {} uploading asset {} to {}", login, file.getOriginalFilename(), broadcaster);
            return channelDirectoryService.createAsset(broadcaster, file)
                    .map(ResponseEntity::ok)
                    .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Unable to read image"));
        } catch (IOException e) {
            LOG.error("Failed to process asset upload for {} by {}", broadcaster, login, e);
            throw new ResponseStatusException(BAD_REQUEST, "Failed to process image", e);
        }
    }

    @PutMapping("/assets/{assetId}/transform")
    public ResponseEntity<AssetView> transform(@PathVariable("broadcaster") String broadcaster,
                                               @PathVariable("assetId") String assetId,
                                               @Valid @RequestBody TransformRequest request,
                                               OAuth2AuthenticationToken authentication) {
        String login = TwitchUser.from(authentication).login();
        ensureAuthorized(broadcaster, login);
        LOG.debug("Applying transform to asset {} on {} by {}", assetId, broadcaster, login);
        return channelDirectoryService.updateTransform(broadcaster, assetId, request)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> {
                    LOG.warn("Transform request for missing asset {} on {} by {}", assetId, broadcaster, login);
                    return new ResponseStatusException(NOT_FOUND, "Asset not found");
                });
    }

    @PostMapping("/assets/{assetId}/play")
    public ResponseEntity<AssetView> play(@PathVariable("broadcaster") String broadcaster,
                                          @PathVariable("assetId") String assetId,
                                          @RequestBody(required = false) PlaybackRequest request,
                                          OAuth2AuthenticationToken authentication) {
        String login = TwitchUser.from(authentication).login();
        ensureAuthorized(broadcaster, login);
        return channelDirectoryService.triggerPlayback(broadcaster, assetId, request)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Asset not found"));
    }

    @PutMapping("/assets/{assetId}/visibility")
    public ResponseEntity<AssetView> visibility(@PathVariable("broadcaster") String broadcaster,
                                                @PathVariable("assetId") String assetId,
                                                @RequestBody VisibilityRequest request,
                                                OAuth2AuthenticationToken authentication) {
        String login = TwitchUser.from(authentication).login();
        ensureAuthorized(broadcaster, login);
        LOG.info("Updating visibility for asset {} on {} by {} to hidden={} ", assetId, broadcaster, login, request.isHidden());
        return channelDirectoryService.updateVisibility(broadcaster, assetId, request)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> {
                    LOG.warn("Visibility update for missing asset {} on {} by {}", assetId, broadcaster, login);
                    return new ResponseStatusException(NOT_FOUND, "Asset not found");
                });
    }

    @GetMapping("/assets/{assetId}/content")
    public ResponseEntity<byte[]> getAssetContent(@PathVariable("broadcaster") String broadcaster,
                                                  @PathVariable("assetId") String assetId,
                                                  OAuth2AuthenticationToken authentication) {
        boolean authorized = false;
        if (authentication != null) {
            String login = TwitchUser.from(authentication).login();
            authorized = channelDirectoryService.isBroadcaster(broadcaster, login)
                    || channelDirectoryService.isAdmin(broadcaster, login);
        }

        if (authorized) {
            LOG.debug("Serving asset {} for broadcaster {} to authenticated user {}", assetId, broadcaster, authentication.getName());
            return channelDirectoryService.getAssetContent(broadcaster, assetId)
                    .map(content -> ResponseEntity.ok()
                            .contentType(MediaType.parseMediaType(content.mediaType()))
                            .body(content.bytes()))
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Asset not found"));
        }

        return channelDirectoryService.getVisibleAssetContent(broadcaster, assetId)
                .map(content -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(content.mediaType()))
                        .body(content.bytes()))
                .orElseThrow(() -> new ResponseStatusException(FORBIDDEN, "Asset not available"));
    }

    @GetMapping("/assets/{assetId}/preview")
    public ResponseEntity<byte[]> getAssetPreview(@PathVariable("broadcaster") String broadcaster,
                                                  @PathVariable("assetId") String assetId,
                                                  OAuth2AuthenticationToken authentication) {
        boolean authorized = false;
        if (authentication != null) {
            String login = TwitchUser.from(authentication).login();
            authorized = channelDirectoryService.isBroadcaster(broadcaster, login)
                    || channelDirectoryService.isAdmin(broadcaster, login);
        }

        if (authorized) {
            LOG.debug("Serving preview for asset {} for broadcaster {}", assetId, broadcaster);
            return channelDirectoryService.getAssetPreview(broadcaster, assetId, true)
                    .map(content -> ResponseEntity.ok()
                            .contentType(MediaType.parseMediaType(content.mediaType()))
                            .body(content.bytes()))
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Preview not found"));
        }

        return channelDirectoryService.getAssetPreview(broadcaster, assetId, false)
                .map(content -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(content.mediaType()))
                        .body(content.bytes()))
                .orElseThrow(() -> new ResponseStatusException(FORBIDDEN, "Preview not available"));
    }

    @DeleteMapping("/assets/{assetId}")
    public ResponseEntity<?> delete(@PathVariable("broadcaster") String broadcaster,
                                    @PathVariable("assetId") String assetId,
                                    OAuth2AuthenticationToken authentication) {
        String login = TwitchUser.from(authentication).login();
        ensureAuthorized(broadcaster, login);
        boolean removed = channelDirectoryService.deleteAsset(broadcaster, assetId);
        if (!removed) {
            LOG.warn("Attempt to delete missing asset {} on {} by {}", assetId, broadcaster, login);
            throw new ResponseStatusException(NOT_FOUND, "Asset not found");
        }
        LOG.info("Asset {} deleted on {} by {}", assetId, broadcaster, login);
        return ResponseEntity.ok().build();
    }

    private void ensureBroadcaster(String broadcaster, String login) {
        if (!channelDirectoryService.isBroadcaster(broadcaster, login)) {
            LOG.warn("Access denied for broadcaster-only action on {} by {}", broadcaster, login);
            throw new ResponseStatusException(FORBIDDEN, "Only broadcasters can manage admins");
        }
    }

    private void ensureAuthorized(String broadcaster, String login) {
        if (!channelDirectoryService.isBroadcaster(broadcaster, login)
                && !channelDirectoryService.isAdmin(broadcaster, login)) {
            LOG.warn("Unauthorized access to channel {} by {}", broadcaster, login);
            throw new ResponseStatusException(FORBIDDEN, "No permission for channel");
        }
    }
}
