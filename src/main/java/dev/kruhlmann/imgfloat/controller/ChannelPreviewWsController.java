package dev.kruhlmann.imgfloat.controller;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Controller;

import dev.kruhlmann.imgfloat.model.OauthSessionUser;
import dev.kruhlmann.imgfloat.model.api.request.TransformRequest;
import dev.kruhlmann.imgfloat.model.api.response.AssetEvent;
import dev.kruhlmann.imgfloat.service.AuthorizationService;
import dev.kruhlmann.imgfloat.service.ChannelDirectoryService;

@Controller
public class ChannelPreviewWsController {

    private final ChannelDirectoryService channelDirectoryService;
    private final AuthorizationService authorizationService;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public ChannelPreviewWsController(
        ChannelDirectoryService channelDirectoryService,
        AuthorizationService authorizationService,
        SimpMessagingTemplate messagingTemplate
    ) {
        this.channelDirectoryService = channelDirectoryService;
        this.authorizationService = authorizationService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/channel/{broadcaster}/assets/{assetId}/preview")
    public void previewTransform(
        @DestinationVariable String broadcaster,
        @DestinationVariable String assetId,
        @Payload @Valid TransformRequest request,
        Principal principal
    ) {
        String sessionUsername = sessionUsername(principal);
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(
            broadcaster,
            sessionUsername
        );
        channelDirectoryService
            .previewTransform(broadcaster, assetId, request)
            .ifPresent((patch) -> messagingTemplate.convertAndSend(
                topicFor(broadcaster),
                AssetEvent.preview(broadcaster, assetId, patch)
            ));
    }

    private String sessionUsername(Principal principal) {
        if (principal instanceof OAuth2AuthenticationToken token) {
            OauthSessionUser user = OauthSessionUser.from(token);
            return user == null ? null : user.login();
        }
        return principal == null ? null : principal.getName();
    }

    private String topicFor(String broadcaster) {
        return "/topic/channel/" + broadcaster.toLowerCase(Locale.ROOT);
    }
}
