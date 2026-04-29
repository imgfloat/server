package dev.kruhlmann.imgfloat.controller;

import dev.kruhlmann.imgfloat.model.OauthSessionUser;
import dev.kruhlmann.imgfloat.model.api.request.ActivePlaylistRequest;
import dev.kruhlmann.imgfloat.model.api.request.PlaylistRequest;
import dev.kruhlmann.imgfloat.model.api.request.PlaylistTrackOrderRequest;
import dev.kruhlmann.imgfloat.model.api.request.PlaylistTrackRequest;
import dev.kruhlmann.imgfloat.model.api.response.PlaylistView;
import dev.kruhlmann.imgfloat.service.AuthorizationService;
import dev.kruhlmann.imgfloat.service.PlaylistService;
import dev.kruhlmann.imgfloat.util.LogSanitizer;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

/**
 * Manages audio playlists for a channel.
 * All endpoints require the caller to be the broadcaster or a channel admin.
 */
@RestController
@RequestMapping("/api/channels/{broadcaster}/playlists")
@SecurityRequirement(name = "twitchOAuth")
public class PlaylistApiController {

    private static final Logger LOG = LoggerFactory.getLogger(PlaylistApiController.class);

    private final PlaylistService playlistService;
    private final AuthorizationService authorizationService;

    public PlaylistApiController(PlaylistService playlistService, AuthorizationService authorizationService) {
        this.playlistService = playlistService;
        this.authorizationService = authorizationService;
    }

    // ── Playlist CRUD ─────────────────────────────────────────────────────

    @GetMapping
    public List<PlaylistView> list(
        @PathVariable("broadcaster") String broadcaster,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(broadcaster, sessionUsername);
        LOG.debug("Listing playlists for {} by {}", LogSanitizer.sanitize(broadcaster), LogSanitizer.sanitize(sessionUsername));
        return playlistService.listPlaylists(broadcaster);
    }

    @PostMapping
    public ResponseEntity<PlaylistView> create(
        @PathVariable("broadcaster") String broadcaster,
        @Valid @RequestBody PlaylistRequest request,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(broadcaster, sessionUsername);
        LOG.info("Creating playlist '{}' for {} by {}", LogSanitizer.sanitize(request.name()),
            LogSanitizer.sanitize(broadcaster), LogSanitizer.sanitize(sessionUsername));
        PlaylistView view = playlistService.createPlaylist(broadcaster, request.name());
        return ResponseEntity.ok(view);
    }

    @PutMapping("/{playlistId}")
    public ResponseEntity<PlaylistView> rename(
        @PathVariable("broadcaster") String broadcaster,
        @PathVariable("playlistId") String playlistId,
        @Valid @RequestBody PlaylistRequest request,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(broadcaster, sessionUsername);
        LOG.info("Renaming playlist {} for {}", LogSanitizer.sanitize(playlistId), LogSanitizer.sanitize(broadcaster));
        return ResponseEntity.ok(playlistService.renamePlaylist(broadcaster, playlistId, request.name()));
    }

    @DeleteMapping("/{playlistId}")
    public ResponseEntity<Void> delete(
        @PathVariable("broadcaster") String broadcaster,
        @PathVariable("playlistId") String playlistId,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(broadcaster, sessionUsername);
        LOG.info("Deleting playlist {} for {}", LogSanitizer.sanitize(playlistId), LogSanitizer.sanitize(broadcaster));
        playlistService.deletePlaylist(broadcaster, playlistId);
        return ResponseEntity.noContent().build();
    }

    // ── Track management ──────────────────────────────────────────────────

    @PostMapping("/{playlistId}/tracks")
    public ResponseEntity<PlaylistView> addTrack(
        @PathVariable("broadcaster") String broadcaster,
        @PathVariable("playlistId") String playlistId,
        @Valid @RequestBody PlaylistTrackRequest request,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(broadcaster, sessionUsername);
        return ResponseEntity.ok(playlistService.addTrack(broadcaster, playlistId, request.audioAssetId()));
    }

    @DeleteMapping("/{playlistId}/tracks/{trackId}")
    public ResponseEntity<PlaylistView> removeTrack(
        @PathVariable("broadcaster") String broadcaster,
        @PathVariable("playlistId") String playlistId,
        @PathVariable("trackId") String trackId,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(broadcaster, sessionUsername);
        return ResponseEntity.ok(playlistService.removeTrack(broadcaster, playlistId, trackId));
    }

    @PutMapping("/{playlistId}/tracks/order")
    public ResponseEntity<PlaylistView> reorderTracks(
        @PathVariable("broadcaster") String broadcaster,
        @PathVariable("playlistId") String playlistId,
        @Valid @RequestBody PlaylistTrackOrderRequest request,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(broadcaster, sessionUsername);
        return ResponseEntity.ok(playlistService.reorderTracks(broadcaster, playlistId, request.trackIds()));
    }

    // ── Active playlist ───────────────────────────────────────────────────

    @GetMapping("/active")
    public ResponseEntity<PlaylistView> getActive(
        @PathVariable("broadcaster") String broadcaster,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(broadcaster, sessionUsername);
        return playlistService.getActivePlaylist(broadcaster)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.noContent().build());
    }

    @PutMapping("/active")
    public ResponseEntity<PlaylistView> selectActive(
        @PathVariable("broadcaster") String broadcaster,
        @RequestBody ActivePlaylistRequest request,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(broadcaster, sessionUsername);
        LOG.info("Selecting active playlist {} for {}", LogSanitizer.sanitize(request.playlistId()),
            LogSanitizer.sanitize(broadcaster));
        return playlistService.selectPlaylist(broadcaster, request.playlistId())
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.noContent().build());
    }

    // ── Playback commands ─────────────────────────────────────────────────

    @PostMapping("/{playlistId}/play")
    public ResponseEntity<Void> play(
        @PathVariable("broadcaster") String broadcaster,
        @PathVariable("playlistId") String playlistId,
        @RequestBody(required = false) java.util.Map<String, String> body,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(broadcaster, sessionUsername);
        String trackId = body != null ? body.get("trackId") : null;
        playlistService.commandPlay(broadcaster, playlistId, trackId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{playlistId}/pause")
    public ResponseEntity<Void> pause(
        @PathVariable("broadcaster") String broadcaster,
        @PathVariable("playlistId") String playlistId,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(broadcaster, sessionUsername);
        playlistService.commandPause(broadcaster, playlistId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{playlistId}/next")
    public ResponseEntity<Void> next(
        @PathVariable("broadcaster") String broadcaster,
        @PathVariable("playlistId") String playlistId,
        @RequestBody java.util.Map<String, String> body,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(broadcaster, sessionUsername);
        playlistService.commandNext(broadcaster, playlistId, body.get("currentTrackId"));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{playlistId}/prev")
    public ResponseEntity<Void> prev(
        @PathVariable("broadcaster") String broadcaster,
        @PathVariable("playlistId") String playlistId,
        @RequestBody java.util.Map<String, String> body,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(broadcaster, sessionUsername);
        playlistService.commandPrev(broadcaster, playlistId, body.get("currentTrackId"));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{playlistId}/track-ended")
    public ResponseEntity<Void> trackEnded(
        @PathVariable("broadcaster") String broadcaster,
        @PathVariable("playlistId") String playlistId,
        @RequestBody java.util.Map<String, String> body,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(broadcaster, sessionUsername);
        playlistService.commandTrackEnded(broadcaster, playlistId, body.get("trackId"));
        return ResponseEntity.ok().build();
    }
}
