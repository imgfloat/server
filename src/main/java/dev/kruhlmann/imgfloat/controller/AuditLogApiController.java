package dev.kruhlmann.imgfloat.controller;

import dev.kruhlmann.imgfloat.model.api.response.AuditLogEntryView;
import dev.kruhlmann.imgfloat.model.api.response.AuditLogPageView;
import dev.kruhlmann.imgfloat.model.OauthSessionUser;
import dev.kruhlmann.imgfloat.service.AuditLogService;
import dev.kruhlmann.imgfloat.service.AuthorizationService;
import dev.kruhlmann.imgfloat.util.LogSanitizer;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.data.domain.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public AuditLogPageView listAuditEntries(
        @PathVariable("broadcaster") String broadcaster,
        @RequestParam(name = "search", required = false) String search,
        @RequestParam(name = "actor", required = false) String actor,
        @RequestParam(name = "action", required = false) String action,
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "25") int size,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userMatchesSessionUsernameOrThrowHttpError(broadcaster, sessionUsername);
        LOG.info(
            "Listing audit log entries for {} by {}",
            LogSanitizer.sanitize(broadcaster),
            LogSanitizer.sanitize(sessionUsername)
        );
        Page<AuditLogEntryView> auditPage = auditLogService
            .listEntries(broadcaster, actor, action, search, page, size)
            .map(AuditLogEntryView::fromEntry);
        return new AuditLogPageView(
            auditPage.getContent(),
            auditPage.getNumber(),
            auditPage.getSize(),
            auditPage.getTotalElements(),
            auditPage.getTotalPages()
        );
    }
}
