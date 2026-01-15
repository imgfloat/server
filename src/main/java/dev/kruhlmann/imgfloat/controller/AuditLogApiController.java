package dev.kruhlmann.imgfloat.controller;

import dev.kruhlmann.imgfloat.model.AuditLogEntryView;
import dev.kruhlmann.imgfloat.model.OauthSessionUser;
import dev.kruhlmann.imgfloat.service.AuditLogService;
import dev.kruhlmann.imgfloat.service.AuthorizationService;
import dev.kruhlmann.imgfloat.util.LogSanitizer;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/channels/{broadcaster}/audit")
@SecurityRequirement(name = "twitchOAuth")
public class AuditLogApiController {

    private static final Logger LOG = LoggerFactory.getLogger(AuditLogApiController.class);
    private final AuditLogService auditLogService;
    private final AuthorizationService authorizationService;

    public AuditLogApiController(AuditLogService auditLogService, AuthorizationService authorizationService) {
        this.auditLogService = auditLogService;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    public List<AuditLogEntryView> listAuditEntries(
        @PathVariable("broadcaster") String broadcaster,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userMatchesSessionUsernameOrThrowHttpError(broadcaster, sessionUsername);
        LOG.info(
            "Listing audit log entries for {} by {}",
            LogSanitizer.sanitize(broadcaster),
            LogSanitizer.sanitize(sessionUsername)
        );
        return auditLogService.listEntries(broadcaster);
    }
}
