package dev.kruhlmann.imgfloat.controller;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import dev.kruhlmann.imgfloat.model.api.request.AdminRequest;
import dev.kruhlmann.imgfloat.model.api.response.AssetView;
import dev.kruhlmann.imgfloat.model.api.request.CanvasSettingsRequest;
import dev.kruhlmann.imgfloat.model.api.request.ChannelScriptSettingsRequest;
import dev.kruhlmann.imgfloat.model.api.request.CodeAssetRequest;
import dev.kruhlmann.imgfloat.model.OauthSessionUser;
import dev.kruhlmann.imgfloat.model.api.request.PlaybackRequest;
import dev.kruhlmann.imgfloat.model.api.response.ScriptAssetAttachmentView;
import dev.kruhlmann.imgfloat.model.api.request.TransformRequest;
import dev.kruhlmann.imgfloat.model.api.response.TwitchUserProfile;
import dev.kruhlmann.imgfloat.model.api.request.VisibilityRequest;
import dev.kruhlmann.imgfloat.service.AuthorizationService;
import dev.kruhlmann.imgfloat.service.ChannelDirectoryService;
import dev.kruhlmann.imgfloat.service.TwitchUserLookupService;
import dev.kruhlmann.imgfloat.util.LogSanitizer;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/channels/{broadcaster}")
@SecurityRequirement(name = "twitchOAuth")
public class ChannelApiController {

    private static final Logger LOG = LoggerFactory.getLogger(ChannelApiController.class);
    private final ChannelDirectoryService channelDirectoryService;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final OAuth2AuthorizedClientRepository authorizedClientRepository;
    private final TwitchUserLookupService twitchUserLookupService;
    private final AuthorizationService authorizationService;

    public ChannelApiController(
        ChannelDirectoryService channelDirectoryService,
        OAuth2AuthorizedClientService authorizedClientService,
        OAuth2AuthorizedClientRepository authorizedClientRepository,
        TwitchUserLookupService twitchUserLookupService,
        AuthorizationService authorizationService
    ) {
        this.channelDirectoryService = channelDirectoryService;
        this.authorizedClientService = authorizedClientService;
        this.authorizedClientRepository = authorizedClientRepository;
        this.twitchUserLookupService = twitchUserLookupService;
        this.authorizationService = authorizationService;
    }

    @PostMapping("/admins")
    public ResponseEntity<Boolean> addAdmin(
        @PathVariable("broadcaster") String broadcaster,
        @Valid @RequestBody AdminRequest request,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        String logBroadcaster = LogSanitizer.sanitize(broadcaster);
        String logSessionUsername = LogSanitizer.sanitize(sessionUsername);
        String logRequestUsername = LogSanitizer.sanitize(request.getUsername());
        authorizationService.userMatchesSessionUsernameOrThrowHttpError(broadcaster, sessionUsername);
        LOG.info("User {} adding admin {} to {}", logSessionUsername, logRequestUsername, logBroadcaster);
        boolean added = channelDirectoryService.addAdmin(broadcaster, request.getUsername(), sessionUsername);
        if (!added) {
            LOG.info("User {} already admin for {} or could not be added", logRequestUsername, logBroadcaster);
        }
        return ResponseEntity.ok(added);
    }

    @GetMapping("/admins")
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
        OAuth2AuthorizedClient authorizedClient = resolveAuthorizedClient(oauthToken, null, request);
        String accessToken = Optional.ofNullable(authorizedClient)
            .map(OAuth2AuthorizedClient::getAccessToken)
            .map((token) -> token.getTokenValue())
            .orElse(null);
        String clientId = Optional.ofNullable(authorizedClient)
            .map(OAuth2AuthorizedClient::getClientRegistration)
            .map((registration) -> registration.getClientId())
            .orElse(null);
        return twitchUserLookupService.fetchProfiles(admins, accessToken, clientId);
    }

    @GetMapping("/admins/suggestions")
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
        OAuth2AuthorizedClient authorizedClient = resolveAuthorizedClient(oauthToken, null, request);

        if (authorizedClient == null) {
            LOG.warn(
                "No authorized Twitch client found for {} while fetching admin suggestions for {}",
                logSessionUsername,
                logBroadcaster
            );
            return List.of();
        }
        String accessToken = Optional.ofNullable(authorizedClient)
            .map(OAuth2AuthorizedClient::getAccessToken)
            .map((token) -> token.getTokenValue())
            .orElse(null);
        String clientId = Optional.ofNullable(authorizedClient)
            .map(OAuth2AuthorizedClient::getClientRegistration)
            .map((registration) -> registration.getClientId())
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

    @DeleteMapping("/admins/{username}")
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
    public CanvasSettingsRequest updateCanvas(
        @PathVariable("broadcaster") String broadcaster,
        @Valid @RequestBody CanvasSettingsRequest request,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        String logBroadcaster = LogSanitizer.sanitize(broadcaster);
        String logSessionUsername = LogSanitizer.sanitize(sessionUsername);
        authorizationService.userMatchesSessionUsernameOrThrowHttpError(broadcaster, sessionUsername);
        LOG.info(
            "Updating canvas for {} by {}: {}x{}",
            logBroadcaster,
            logSessionUsername,
            request.getWidth(),
            request.getHeight()
        );
        return channelDirectoryService.updateCanvasSettings(broadcaster, request, sessionUsername);
    }

    @GetMapping("/settings")
    public ChannelScriptSettingsRequest getScriptSettings(@PathVariable("broadcaster") String broadcaster) {
        return channelDirectoryService.getChannelScriptSettings(broadcaster);
    }

    @PutMapping("/settings")
    public ChannelScriptSettingsRequest updateScriptSettings(
        @PathVariable("broadcaster") String broadcaster,
        @Valid @RequestBody ChannelScriptSettingsRequest request,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        String logBroadcaster = LogSanitizer.sanitize(broadcaster);
        String logSessionUsername = LogSanitizer.sanitize(sessionUsername);
        authorizationService.userMatchesSessionUsernameOrThrowHttpError(broadcaster, sessionUsername);
        LOG.info("Updating script settings for {} by {}", logBroadcaster, logSessionUsername);
        return channelDirectoryService.updateChannelScriptSettings(broadcaster, request, sessionUsername);
    }

    @PostMapping(value = "/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AssetView> createAsset(
        @PathVariable("broadcaster") String broadcaster,
        @org.springframework.web.bind.annotation.RequestPart("file") MultipartFile file,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        String logBroadcaster = LogSanitizer.sanitize(broadcaster);
        String logSessionUsername = LogSanitizer.sanitize(sessionUsername);
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(
            broadcaster,
            sessionUsername
        );
        if (file == null || file.isEmpty()) {
            LOG.warn("User {} attempted to upload empty file to {}", logSessionUsername, logBroadcaster);
            throw new ResponseStatusException(BAD_REQUEST, "Asset file is required");
        }
        try {
            String logOriginalFilename = LogSanitizer.sanitize(file.getOriginalFilename());
            LOG.info("User {} uploading asset {} to {}", logSessionUsername, logOriginalFilename, logBroadcaster);
            return channelDirectoryService
                .createAsset(broadcaster, file, sessionUsername)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Unable to read image"));
        } catch (IOException e) {
            LOG.error("Failed to process asset upload for {} by {}", logBroadcaster, logSessionUsername, e);
            throw new ResponseStatusException(BAD_REQUEST, "Failed to process image", e);
        }
    }

    @PostMapping("/assets/code")
    public ResponseEntity<AssetView> createCodeAsset(
        @PathVariable("broadcaster") String broadcaster,
        @Valid @RequestBody CodeAssetRequest request,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        String logBroadcaster = LogSanitizer.sanitize(broadcaster);
        String logSessionUsername = LogSanitizer.sanitize(sessionUsername);
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(
            broadcaster,
            sessionUsername
        );
        LOG.info("Creating custom script for {} by {}", logBroadcaster, logSessionUsername);
        return channelDirectoryService
            .createCodeAsset(broadcaster, request, sessionUsername)
            .map(ResponseEntity::ok)
            .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Unable to save custom script"));
    }

    @PutMapping("/assets/{assetId}/code")
    public ResponseEntity<AssetView> updateCodeAsset(
        @PathVariable("broadcaster") String broadcaster,
        @PathVariable("assetId") String assetId,
        @Valid @RequestBody CodeAssetRequest request,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        String logBroadcaster = LogSanitizer.sanitize(broadcaster);
        String logSessionUsername = LogSanitizer.sanitize(sessionUsername);
        String logAssetId = LogSanitizer.sanitize(assetId);
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(
            broadcaster,
            sessionUsername
        );
        LOG.info("Updating custom script {} for {} by {}", logAssetId, logBroadcaster, logSessionUsername);
        return channelDirectoryService
            .updateCodeAsset(broadcaster, assetId, request, sessionUsername)
            .map(ResponseEntity::ok)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Asset not found"));
    }

    @PutMapping("/assets/{assetId}/transform")
    public ResponseEntity<AssetView> transform(
        @PathVariable("broadcaster") String broadcaster,
        @PathVariable("assetId") String assetId,
        @Valid @RequestBody TransformRequest request,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        String logBroadcaster = LogSanitizer.sanitize(broadcaster);
        String logAssetId = LogSanitizer.sanitize(assetId);
        String logSessionUsername = LogSanitizer.sanitize(sessionUsername);
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(
            broadcaster,
            sessionUsername
        );
        LOG.debug("Applying transform to asset {} on {} by {}", logAssetId, logBroadcaster, logSessionUsername);
        return channelDirectoryService
            .updateTransform(broadcaster, assetId, request, sessionUsername)
            .map(ResponseEntity::ok)
            .orElseThrow(() -> {
                LOG.warn(
                    "Transform request for missing asset {} on {} by {}",
                    logAssetId,
                    logBroadcaster,
                    logSessionUsername
                );
                return createAsset404();
            });
    }

    @PostMapping("/assets/{assetId}/play")
    public ResponseEntity<AssetView> play(
        @PathVariable("broadcaster") String broadcaster,
        @PathVariable("assetId") String assetId,
        @RequestBody(required = false) PlaybackRequest request,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        String logBroadcaster = LogSanitizer.sanitize(broadcaster);
        String logAssetId = LogSanitizer.sanitize(assetId);
        String logSessionUsername = LogSanitizer.sanitize(sessionUsername);
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(
            broadcaster,
            sessionUsername
        );
        LOG.info("Triggering playback for asset {} on {} by {}", logAssetId, logBroadcaster, logSessionUsername);
        return channelDirectoryService
            .triggerPlayback(broadcaster, assetId, request, sessionUsername)
            .map(ResponseEntity::ok)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Asset not found"));
    }

    @PutMapping("/assets/{assetId}/visibility")
    public ResponseEntity<AssetView> visibility(
        @PathVariable("broadcaster") String broadcaster,
        @PathVariable("assetId") String assetId,
        @RequestBody VisibilityRequest request,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        String logBroadcaster = LogSanitizer.sanitize(broadcaster);
        String logAssetId = LogSanitizer.sanitize(assetId);
        String logSessionUsername = LogSanitizer.sanitize(sessionUsername);
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(
            broadcaster,
            sessionUsername
        );
        LOG.info(
            "Updating visibility for asset {} on {} by {} to hidden={} ",
            logAssetId,
            logBroadcaster,
            logSessionUsername,
            request.isHidden()
        );
        return channelDirectoryService
            .updateVisibility(broadcaster, assetId, request, sessionUsername)
            .map(ResponseEntity::ok)
            .orElseThrow(() -> {
                LOG.warn(
                    "Visibility update for missing asset {} on {} by {}",
                    logAssetId,
                    logBroadcaster,
                    logSessionUsername
                );
                return createAsset404();
            });
    }

    @GetMapping("/assets/{assetId}/content")
    public ResponseEntity<byte[]> getAssetContent(
        @PathVariable("broadcaster") String broadcaster,
        @PathVariable("assetId") String assetId
    ) {
        String logBroadcaster = LogSanitizer.sanitize(broadcaster);
        String logAssetId = LogSanitizer.sanitize(assetId);
        LOG.debug("Serving asset {} for broadcaster {}", logAssetId, logBroadcaster);
        return channelDirectoryService
            .getAssetContent(assetId)
            .map((content) ->
                ResponseEntity.ok()
                    .header("X-Content-Type-Options", "nosniff")
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDispositionFor(content.mediaType()))
                    .contentType(MediaType.parseMediaType(content.mediaType()))
                    .body(content.bytes())
            )
            .orElseThrow(() -> createAsset404());
    }

    @GetMapping("/script-assets/{assetId}/attachments/{attachmentId}/content")
    public ResponseEntity<byte[]> getScriptAttachmentContent(
        @PathVariable("broadcaster") String broadcaster,
        @PathVariable("assetId") String assetId,
        @PathVariable("attachmentId") String attachmentId
    ) {
        String logBroadcaster = LogSanitizer.sanitize(broadcaster);
        String logAssetId = LogSanitizer.sanitize(assetId);
        String logAttachmentId = LogSanitizer.sanitize(attachmentId);
        LOG.debug(
            "Serving script attachment {} for asset {} for broadcaster {}",
            logAttachmentId,
            logAssetId,
            logBroadcaster
        );
        return channelDirectoryService
            .getScriptAttachmentContent(broadcaster, assetId, attachmentId)
            .map((content) ->
                ResponseEntity.ok()
                    .header("X-Content-Type-Options", "nosniff")
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDispositionFor(content.mediaType()))
                    .contentType(MediaType.parseMediaType(content.mediaType()))
                    .body(content.bytes())
            )
            .orElseThrow(() -> createAsset404());
    }

    @GetMapping("/assets/{assetId}/logo")
    public ResponseEntity<byte[]> getScriptLogo(
        @PathVariable("broadcaster") String broadcaster,
        @PathVariable("assetId") String assetId,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(
            broadcaster,
            sessionUsername
        );
        return channelDirectoryService
            .getScriptLogoContent(broadcaster, assetId)
            .map((content) ->
                ResponseEntity.ok()
                    .header("X-Content-Type-Options", "nosniff")
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDispositionFor(content.mediaType()))
                    .contentType(MediaType.parseMediaType(content.mediaType()))
                    .body(content.bytes())
            )
            .orElseThrow(() -> createAsset404());
    }

    @GetMapping("/assets/{assetId}/preview")
    public ResponseEntity<byte[]> getAssetPreview(
        @PathVariable("broadcaster") String broadcaster,
        @PathVariable("assetId") String assetId
    ) {
        String logBroadcaster = LogSanitizer.sanitize(broadcaster);
        String logAssetId = LogSanitizer.sanitize(assetId);
        LOG.debug("Serving preview for asset {} for broadcaster {}", logAssetId, logBroadcaster);
        return channelDirectoryService
            .getAssetPreview(assetId, true)
            .map((content) ->
                ResponseEntity.ok()
                    .header("X-Content-Type-Options", "nosniff")
                    .contentType(MediaType.parseMediaType(content.mediaType()))
                    .body(content.bytes())
            )
            .orElseThrow(() -> createAsset404());
    }

    private String contentDispositionFor(String mediaType) {
        if (
            mediaType != null &&
            dev.kruhlmann.imgfloat.service.media.MediaDetectionService.isInlineDisplayType(mediaType)
        ) {
            return "inline";
        }
        return "attachment";
    }

    @DeleteMapping("/assets/{assetId}")
    public ResponseEntity<Void> delete(
        @PathVariable("broadcaster") String broadcaster,
        @PathVariable("assetId") String assetId,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        String logBroadcaster = LogSanitizer.sanitize(broadcaster);
        String logAssetId = LogSanitizer.sanitize(assetId);
        String logSessionUsername = LogSanitizer.sanitize(sessionUsername);
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(
            broadcaster,
            sessionUsername
        );
        boolean removed = channelDirectoryService.deleteAsset(assetId, sessionUsername);
        if (!removed) {
            LOG.warn("Attempt to delete missing asset {} on {} by {}", logAssetId, logBroadcaster, logSessionUsername);
            throw createAsset404();
        }
        LOG.info("Asset {} deleted on {} by {}", logAssetId, logBroadcaster, logSessionUsername);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/assets/{assetId}/attachments")
    public Collection<ScriptAssetAttachmentView> listScriptAttachments(
        @PathVariable("broadcaster") String broadcaster,
        @PathVariable("assetId") String assetId,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(
            broadcaster,
            sessionUsername
        );
        return channelDirectoryService.listScriptAttachments(broadcaster, assetId);
    }

    @PostMapping(value = "/assets/{assetId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ScriptAssetAttachmentView> createScriptAttachment(
        @PathVariable("broadcaster") String broadcaster,
        @PathVariable("assetId") String assetId,
        @RequestPart("file") MultipartFile file,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(
            broadcaster,
            sessionUsername
        );
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Attachment file is required");
        }
        try {
            return channelDirectoryService
                .createScriptAttachment(broadcaster, assetId, file, sessionUsername)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Unable to save attachment"));
        } catch (IOException e) {
            LOG.error("Failed to process attachment upload for {} by {}", broadcaster, sessionUsername, e);
            throw new ResponseStatusException(BAD_REQUEST, "Failed to process attachment", e);
        }
    }

    @PostMapping(value = "/assets/{assetId}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AssetView> updateScriptLogo(
        @PathVariable("broadcaster") String broadcaster,
        @PathVariable("assetId") String assetId,
        @RequestPart("file") MultipartFile file,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(
            broadcaster,
            sessionUsername
        );
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Logo file is required");
        }
        try {
            return channelDirectoryService
                .updateScriptLogo(broadcaster, assetId, file, sessionUsername)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Unable to save logo"));
        } catch (IOException e) {
            LOG.error("Failed to process logo upload for {} by {}", broadcaster, sessionUsername, e);
            throw new ResponseStatusException(BAD_REQUEST, "Failed to process logo", e);
        }
    }

    @DeleteMapping("/assets/{assetId}/logo")
    public ResponseEntity<Void> deleteScriptLogo(
        @PathVariable("broadcaster") String broadcaster,
        @PathVariable("assetId") String assetId,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(
            broadcaster,
            sessionUsername
        );
        channelDirectoryService.clearScriptLogo(broadcaster, assetId, sessionUsername);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/assets/{assetId}/attachments/{attachmentId}")
    public ResponseEntity<Void> deleteScriptAttachment(
        @PathVariable("broadcaster") String broadcaster,
        @PathVariable("assetId") String assetId,
        @PathVariable("attachmentId") String attachmentId,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(
            broadcaster,
            sessionUsername
        );
        boolean removed = channelDirectoryService.deleteScriptAttachment(broadcaster, assetId, attachmentId, sessionUsername);
        if (!removed) {
            throw createAsset404();
        }
        return ResponseEntity.ok().build();
    }

    private ResponseStatusException createAsset404() {
        return new ResponseStatusException(NOT_FOUND, "Asset not found");
    }

    private OAuth2AuthorizedClient resolveAuthorizedClient(
        OAuth2AuthenticationToken oauthToken,
        OAuth2AuthorizedClient authorizedClient,
        HttpServletRequest request
    ) {
        if (authorizedClient != null) {
            return authorizedClient;
        }
        if (oauthToken == null) {
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
