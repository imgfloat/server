package com.imgfloat.app.controller;

import com.imgfloat.app.model.AdminRequest;
import com.imgfloat.app.model.Asset;
import com.imgfloat.app.model.CanvasSettingsRequest;
import com.imgfloat.app.model.TransformRequest;
import com.imgfloat.app.model.VisibilityRequest;
import com.imgfloat.app.service.ChannelDirectoryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
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

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/channels/{broadcaster}")
public class ChannelApiController {
    private final ChannelDirectoryService channelDirectoryService;

    public ChannelApiController(ChannelDirectoryService channelDirectoryService) {
        this.channelDirectoryService = channelDirectoryService;
    }

    @PostMapping("/admins")
    public ResponseEntity<?> addAdmin(@PathVariable("broadcaster") String broadcaster,
                                      @Valid @RequestBody AdminRequest request,
                                      OAuth2AuthenticationToken authentication) {
        String login = TwitchUser.from(authentication).login();
        ensureBroadcaster(broadcaster, login);
        boolean added = channelDirectoryService.addAdmin(broadcaster, request.getUsername());
        return ResponseEntity.ok().body(added);
    }

    @GetMapping("/admins")
    public Collection<String> listAdmins(@PathVariable("broadcaster") String broadcaster,
                                         OAuth2AuthenticationToken authentication) {
        String login = TwitchUser.from(authentication).login();
        ensureBroadcaster(broadcaster, login);
        return channelDirectoryService.getOrCreateChannel(broadcaster).getAdmins();
    }

    @DeleteMapping("/admins/{username}")
    public ResponseEntity<?> removeAdmin(@PathVariable("broadcaster") String broadcaster,
                                         @PathVariable("username") String username,
                                         OAuth2AuthenticationToken authentication) {
        String login = TwitchUser.from(authentication).login();
        ensureBroadcaster(broadcaster, login);
        boolean removed = channelDirectoryService.removeAdmin(broadcaster, username);
        return ResponseEntity.ok().body(removed);
    }

    @GetMapping("/assets")
    public Collection<Asset> listAssets(@PathVariable("broadcaster") String broadcaster,
                                        OAuth2AuthenticationToken authentication) {
        String login = TwitchUser.from(authentication).login();
        if (!channelDirectoryService.isBroadcaster(broadcaster, login)
                && !channelDirectoryService.isAdmin(broadcaster, login)) {
            throw new ResponseStatusException(FORBIDDEN, "Not authorized");
        }
        return channelDirectoryService.getAssetsForAdmin(broadcaster);
    }

    @GetMapping("/assets/visible")
    public Collection<Asset> listVisible(@PathVariable("broadcaster") String broadcaster,
                                         OAuth2AuthenticationToken authentication) {
        String login = TwitchUser.from(authentication).login();
        if (!channelDirectoryService.isBroadcaster(broadcaster, login)) {
            throw new ResponseStatusException(FORBIDDEN, "Only broadcaster can load public overlay");
        }
        return channelDirectoryService.getVisibleAssets(broadcaster);
    }

    @GetMapping("/canvas")
    public CanvasSettingsRequest getCanvas(@PathVariable("broadcaster") String broadcaster,
                                           OAuth2AuthenticationToken authentication) {
        String login = TwitchUser.from(authentication).login();
        ensureAuthorized(broadcaster, login);
        return channelDirectoryService.getCanvasSettings(broadcaster);
    }

    @PutMapping("/canvas")
    public CanvasSettingsRequest updateCanvas(@PathVariable("broadcaster") String broadcaster,
                                              @Valid @RequestBody CanvasSettingsRequest request,
                                              OAuth2AuthenticationToken authentication) {
        String login = TwitchUser.from(authentication).login();
        ensureBroadcaster(broadcaster, login);
        return channelDirectoryService.updateCanvasSettings(broadcaster, request);
    }

    @PostMapping(value = "/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Asset> createAsset(@PathVariable("broadcaster") String broadcaster,
                                             @org.springframework.web.bind.annotation.RequestPart("file") MultipartFile file,
                                             OAuth2AuthenticationToken authentication) {
        String login = TwitchUser.from(authentication).login();
        ensureAuthorized(broadcaster, login);
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Asset file is required");
        }
        try {
            return channelDirectoryService.createAsset(broadcaster, file)
                    .map(ResponseEntity::ok)
                    .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Unable to read image"));
        } catch (IOException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Failed to process image", e);
        }
    }

    @PutMapping("/assets/{assetId}/transform")
    public ResponseEntity<Asset> transform(@PathVariable("broadcaster") String broadcaster,
                                           @PathVariable("assetId") String assetId,
                                           @Valid @RequestBody TransformRequest request,
                                           OAuth2AuthenticationToken authentication) {
        String login = TwitchUser.from(authentication).login();
        ensureAuthorized(broadcaster, login);
        return channelDirectoryService.updateTransform(broadcaster, assetId, request)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Asset not found"));
    }

    @PutMapping("/assets/{assetId}/visibility")
    public ResponseEntity<Asset> visibility(@PathVariable("broadcaster") String broadcaster,
                                            @PathVariable("assetId") String assetId,
                                            @RequestBody VisibilityRequest request,
                                            OAuth2AuthenticationToken authentication) {
        String login = TwitchUser.from(authentication).login();
        ensureAuthorized(broadcaster, login);
        return channelDirectoryService.updateVisibility(broadcaster, assetId, request)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Asset not found"));
    }

    @DeleteMapping("/assets/{assetId}")
    public ResponseEntity<?> delete(@PathVariable("broadcaster") String broadcaster,
                                    @PathVariable("assetId") String assetId,
                                    OAuth2AuthenticationToken authentication) {
        String login = TwitchUser.from(authentication).login();
        ensureAuthorized(broadcaster, login);
        boolean removed = channelDirectoryService.deleteAsset(broadcaster, assetId);
        if (!removed) {
            throw new ResponseStatusException(NOT_FOUND, "Asset not found");
        }
        return ResponseEntity.ok().build();
    }

    private void ensureBroadcaster(String broadcaster, String login) {
        if (!channelDirectoryService.isBroadcaster(broadcaster, login)) {
            throw new ResponseStatusException(FORBIDDEN, "Only broadcasters can manage admins");
        }
    }

    private void ensureAuthorized(String broadcaster, String login) {
        if (!channelDirectoryService.isBroadcaster(broadcaster, login)
                && !channelDirectoryService.isAdmin(broadcaster, login)) {
            throw new ResponseStatusException(FORBIDDEN, "No permission for channel");
        }
    }
}
