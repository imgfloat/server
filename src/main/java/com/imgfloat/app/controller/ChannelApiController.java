package com.imgfloat.app.controller;

import com.imgfloat.app.model.AdminRequest;
import com.imgfloat.app.model.Asset;
import com.imgfloat.app.model.AssetRequest;
import com.imgfloat.app.model.TransformRequest;
import com.imgfloat.app.model.VisibilityRequest;
import com.imgfloat.app.service.ChannelDirectoryService;
import jakarta.validation.Valid;
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
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

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

    @PostMapping("/assets")
    public ResponseEntity<Asset> createAsset(@PathVariable("broadcaster") String broadcaster,
                                             @Valid @RequestBody AssetRequest request,
                                             OAuth2AuthenticationToken authentication) {
        String login = TwitchUser.from(authentication).login();
        ensureAuthorized(broadcaster, login);
        return channelDirectoryService.createAsset(broadcaster, request)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Channel not found"));
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
