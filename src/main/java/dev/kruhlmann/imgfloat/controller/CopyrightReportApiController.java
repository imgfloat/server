package dev.kruhlmann.imgfloat.controller;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import dev.kruhlmann.imgfloat.model.OauthSessionUser;
import dev.kruhlmann.imgfloat.model.api.request.CopyrightReportRequest;
import dev.kruhlmann.imgfloat.model.api.request.CopyrightReportReviewRequest;
import dev.kruhlmann.imgfloat.model.api.response.CopyrightReportPageView;
import dev.kruhlmann.imgfloat.model.api.response.CopyrightReportView;
import dev.kruhlmann.imgfloat.model.db.imgfloat.CopyrightReportStatus;
import dev.kruhlmann.imgfloat.service.AuthorizationService;
import dev.kruhlmann.imgfloat.service.CopyrightReportService;
import dev.kruhlmann.imgfloat.util.LogSanitizer;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping
public class CopyrightReportApiController {

    private static final Logger LOG = LoggerFactory.getLogger(CopyrightReportApiController.class);

    private final CopyrightReportService copyrightReportService;
    private final AuthorizationService authorizationService;

    public CopyrightReportApiController(
        CopyrightReportService copyrightReportService,
        AuthorizationService authorizationService
    ) {
        this.copyrightReportService = copyrightReportService;
        this.authorizationService = authorizationService;
    }

    /**
     * Public endpoint — no authentication required. Rate limiting is applied via
     * {@link dev.kruhlmann.imgfloat.config.RateLimitInterceptor}.
     */
    @PostMapping("/api/assets/{assetId}/copyright-reports")
    public ResponseEntity<CopyrightReportView> submitReport(
        @PathVariable("assetId") String assetId,
        @Valid @RequestBody CopyrightReportRequest request
    ) {
        LOG.info("Copyright report submitted for asset {}", LogSanitizer.sanitize(assetId));
        var report = copyrightReportService.submitReport(assetId, request);
        return ResponseEntity.ok(CopyrightReportView.fromReport(report));
    }

    @GetMapping("/api/copyright-reports")
    @SecurityRequirement(name = "administrator")
    public CopyrightReportPageView listReports(
        @RequestParam(name = "status", required = false) CopyrightReportStatus status,
        @RequestParam(name = "broadcaster", required = false) String broadcaster,
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "25") int size,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userIsSystemAdministratorOrThrowHttpError(sessionUsername);
        Page<CopyrightReportView> result = copyrightReportService
            .listReports(status, broadcaster, page, size)
            .map(CopyrightReportView::fromReport);
        return new CopyrightReportPageView(
            result.getContent(),
            result.getNumber(),
            result.getSize(),
            result.getTotalElements(),
            result.getTotalPages()
        );
    }

    @GetMapping("/api/copyright-reports/{reportId}")
    @SecurityRequirement(name = "administrator")
    public CopyrightReportView getReport(
        @PathVariable("reportId") String reportId,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userIsSystemAdministratorOrThrowHttpError(sessionUsername);
        return CopyrightReportView.fromReport(copyrightReportService.getReport(reportId));
    }

    @PostMapping("/api/copyright-reports/{reportId}/review")
    @SecurityRequirement(name = "administrator")
    public CopyrightReportView reviewReport(
        @PathVariable("reportId") String reportId,
        @Valid @RequestBody CopyrightReportReviewRequest request,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userIsSystemAdministratorOrThrowHttpError(sessionUsername);
        LOG.info(
            "Copyright report {} reviewed by {} with action {}",
            reportId,
            LogSanitizer.sanitize(sessionUsername),
            request.action()
        );
        try {
            return CopyrightReportView.fromReport(
                copyrightReportService.reviewReport(reportId, request, sessionUsername)
            );
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(BAD_REQUEST, e.getMessage(), e);
        }
    }
}
