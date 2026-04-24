package dev.kruhlmann.imgfloat.controller;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import dev.kruhlmann.imgfloat.model.api.request.CodeAssetRequest;
import dev.kruhlmann.imgfloat.model.api.response.AssetView;
import dev.kruhlmann.imgfloat.model.api.response.ScriptAssetAttachmentView;
import dev.kruhlmann.imgfloat.model.OauthSessionUser;
import dev.kruhlmann.imgfloat.service.AuthorizationService;
import dev.kruhlmann.imgfloat.service.ChannelDirectoryService;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Manages script assets, logos, and attachments for a specific broadcaster channel.
 * General asset management is handled by {@link ChannelApiController}.
 */
@RestController
@RequestMapping("/api/channels/{broadcaster}")
@SecurityRequirement(name = "twitchOAuth")
public class ChannelScriptAssetApiController {

    private static final Logger LOG = LoggerFactory.getLogger(ChannelScriptAssetApiController.class);
    private final ChannelDirectoryService channelDirectoryService;
    private final AuthorizationService authorizationService;

    public ChannelScriptAssetApiController(
        ChannelDirectoryService channelDirectoryService,
        AuthorizationService authorizationService
    ) {
        this.channelDirectoryService = channelDirectoryService;
        this.authorizationService = authorizationService;
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
            .orElseThrow(this::createAsset404);
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
            .orElseThrow(this::createAsset404);
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
