package dev.kruhlmann.imgfloat.controller;

import dev.kruhlmann.imgfloat.model.OauthSessionUser;
import dev.kruhlmann.imgfloat.service.AuthorizationService;
import dev.kruhlmann.imgfloat.service.PlaylistService;
import java.security.Principal;
import java.util.Map;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Controller;

@Controller
public class PlaylistWsController {

    private final PlaylistService playlistService;
    private final AuthorizationService authorizationService;

    public PlaylistWsController(PlaylistService playlistService, AuthorizationService authorizationService) {
        this.playlistService = playlistService;
        this.authorizationService = authorizationService;
    }

    /**
     * Periodic position heartbeat sent by the broadcast renderer over STOMP.
     * Payload: { "trackId": "...", "position": 42.5 }
     */
    @MessageMapping("/channel/{broadcaster}/playlists/{playlistId}/position")
    public void reportPosition(
        @DestinationVariable String broadcaster,
        @DestinationVariable String playlistId,
        @Payload Map<String, Object> payload,
        Principal principal
    ) {
        String sessionUsername = sessionUsername(principal);
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(broadcaster, sessionUsername);
        String trackId = (String) payload.get("trackId");
        double position = payload.get("position") instanceof Number n ? n.doubleValue() : 0.0;
        playlistService.reportPosition(broadcaster, playlistId, trackId, position);
    }

    private String sessionUsername(Principal principal) {
        if (principal instanceof OAuth2AuthenticationToken token) {
            OauthSessionUser user = OauthSessionUser.from(token);
            return user == null ? null : user.login();
        }
        return principal == null ? null : principal.getName();
    }
}
