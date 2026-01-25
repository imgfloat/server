package dev.kruhlmann.imgfloat.controller;

import dev.kruhlmann.imgfloat.util.LogSanitizer;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/session")
@SecurityRequirement(name = "twitchOAuth")
public class SessionApiController {

    private static final Logger LOG = LoggerFactory.getLogger(SessionApiController.class);
    private final OAuth2AuthorizedClientManager authorizedClientManager;

    public SessionApiController(OAuth2AuthorizedClientManager authorizedClientManager) {
        this.authorizedClientManager = authorizedClientManager;
    }

    @GetMapping("/refresh")
    public ResponseEntity<Void> refreshSession(
        OAuth2AuthenticationToken oauthToken,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        if (oauthToken == null) {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).build();
        }
        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
            .withClientRegistrationId(oauthToken.getAuthorizedClientRegistrationId())
            .principal(oauthToken)
            .attribute(HttpServletRequest.class.getName(), request)
            .attribute(HttpServletResponse.class.getName(), response)
            .build();
        OAuth2AuthorizedClient authorizedClient = authorizedClientManager.authorize(authorizeRequest);
        if (authorizedClient == null) {
            LOG.warn(
                "Failed to refresh session for {}",
                LogSanitizer.sanitize(oauthToken.getName())
            );
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).build();
    }
}
