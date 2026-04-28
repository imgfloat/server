package dev.kruhlmann.imgfloat.controller;

import dev.kruhlmann.imgfloat.model.OauthSessionUser;
import dev.kruhlmann.imgfloat.model.api.response.CopyrightReportView;
import dev.kruhlmann.imgfloat.service.AuthorizationService;
import dev.kruhlmann.imgfloat.service.CopyrightReportService;
import dev.kruhlmann.imgfloat.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Broadcaster-facing endpoints for copyright notices — reports that have been
 * escalated to the broadcaster by a sysadmin via the NOTIFY_BROADCASTER action.
 * These remain visible to the broadcaster until they explicitly dismiss them.
 */
@RestController
@RequestMapping("/api/channels/{broadcaster}/copyright-notices")
public class BroadcasterCopyrightNoticeApiController {

    private static final Logger LOG = LoggerFactory.getLogger(BroadcasterCopyrightNoticeApiController.class);

    private final CopyrightReportService copyrightReportService;
    private final AuthorizationService authorizationService;

    public BroadcasterCopyrightNoticeApiController(
        CopyrightReportService copyrightReportService,
        AuthorizationService authorizationService
    ) {
        this.copyrightReportService = copyrightReportService;
        this.authorizationService = authorizationService;
    }

    /** List all pending (NOTIFIED) copyright notices for this broadcaster. */
    @GetMapping
    public List<CopyrightReportView> listNotices(
        @PathVariable("broadcaster") String broadcaster,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(broadcaster, sessionUsername);
        return copyrightReportService.listNotices(broadcaster)
            .stream()
            .map(CopyrightReportView::fromReport)
            .toList();
    }

    /** Broadcaster acknowledges and dismisses a notice (transitions it to RESOLVED). */
    @PostMapping("/{reportId}/dismiss")
    public ResponseEntity<Void> dismissNotice(
        @PathVariable("broadcaster") String broadcaster,
        @PathVariable("reportId") String reportId,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(broadcaster, sessionUsername);
        LOG.info("Copyright notice {} dismissed by {} for broadcaster {}",
            reportId,
            LogSanitizer.sanitize(sessionUsername),
            LogSanitizer.sanitize(broadcaster)
        );
        copyrightReportService.dismissNotice(reportId, broadcaster);
        return ResponseEntity.noContent().build();
    }
}
