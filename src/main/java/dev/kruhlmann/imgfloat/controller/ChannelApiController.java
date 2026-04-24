package dev.kruhlmann.imgfloat.controller;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import dev.kruhlmann.imgfloat.model.api.request.AssetOrderRequest;
import dev.kruhlmann.imgfloat.model.api.request.CanvasSettingsRequest;
import dev.kruhlmann.imgfloat.model.api.request.ChannelScriptSettingsRequest;
import dev.kruhlmann.imgfloat.model.api.request.PlaybackRequest;
import dev.kruhlmann.imgfloat.model.api.request.TransformRequest;
import dev.kruhlmann.imgfloat.model.api.request.VisibilityRequest;
import dev.kruhlmann.imgfloat.model.api.response.AssetView;
import dev.kruhlmann.imgfloat.model.OauthSessionUser;
import dev.kruhlmann.imgfloat.service.AuthorizationService;
import dev.kruhlmann.imgfloat.service.ChannelDirectoryService;
import dev.kruhlmann.imgfloat.service.ChannelSettingsService;
import dev.kruhlmann.imgfloat.service.media.MediaDetectionService;
import dev.kruhlmann.imgfloat.util.LogSanitizer;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Manages assets, canvas settings, and playback for a specific broadcaster channel.
 * Admin management is handled by {@link ChannelAdminApiController}.
 * Script asset management (code assets, logos, attachments) is handled by {@link ChannelScriptAssetApiController}.
 */
@RestController
@RequestMapping("/api/channels/{broadcaster}")
@SecurityRequirement(name = "twitchOAuth")
public class ChannelApiController {

    private static final Logger LOG = LoggerFactory.getLogger(ChannelApiController.class);
    private final ChannelDirectoryService channelDirectoryService;
    private final ChannelSettingsService channelSettingsService;
    private final AuthorizationService authorizationService;

    public ChannelApiController(
        ChannelDirectoryService channelDirectoryService,
        ChannelSettingsService channelSettingsService,
        AuthorizationService authorizationService
    ) {
        this.channelDirectoryService = channelDirectoryService;
        this.channelSettingsService = channelSettingsService;
        this.authorizationService = authorizationService;
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
        return channelSettingsService.getCanvasSettings(broadcaster);
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
        return channelSettingsService.updateCanvasSettings(broadcaster, request, sessionUsername);
    }

    @GetMapping("/settings")
    public ChannelScriptSettingsRequest getScriptSettings(@PathVariable("broadcaster") String broadcaster) {
        return channelSettingsService.getChannelScriptSettings(broadcaster);
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
        return channelSettingsService.updateChannelScriptSettings(broadcaster, request, sessionUsername);
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

    @PostMapping("/assets/order")
    public ResponseEntity<Void> reorderAssets(
        @PathVariable("broadcaster") String broadcaster,
        @Valid @RequestBody AssetOrderRequest request,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        String logBroadcaster = LogSanitizer.sanitize(broadcaster);
        String logSessionUsername = LogSanitizer.sanitize(sessionUsername);
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(
            broadcaster,
            sessionUsername
        );
        LOG.debug("Reordering assets for {} by {}", logBroadcaster, logSessionUsername);
        channelDirectoryService.reorderAssets(broadcaster, request.getUpdates(), sessionUsername);
        return ResponseEntity.noContent().build();
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
            .orElseThrow(this::createAsset404);
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
            .orElseThrow(this::createAsset404);
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

    private String contentDispositionFor(String mediaType) {
        if (MediaDetectionService.isInlineDisplayType(mediaType)) {
            return "inline";
        }
        return "attachment";
    }

    private ResponseStatusException createAsset404() {
        return new ResponseStatusException(NOT_FOUND, "Asset not found");
    }
}
