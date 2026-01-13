package dev.kruhlmann.imgfloat.controller;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import dev.kruhlmann.imgfloat.model.AssetView;
import dev.kruhlmann.imgfloat.model.OauthSessionUser;
import dev.kruhlmann.imgfloat.model.ScriptMarketplaceEntry;
import dev.kruhlmann.imgfloat.model.ScriptMarketplaceImportRequest;
import dev.kruhlmann.imgfloat.service.AuthorizationService;
import dev.kruhlmann.imgfloat.service.ChannelDirectoryService;
import dev.kruhlmann.imgfloat.util.LogSanitizer;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
@RequestMapping("/api/marketplace")
@SecurityRequirement(name = "twitchOAuth")
public class ScriptMarketplaceController {

    private static final Logger LOG = LoggerFactory.getLogger(ScriptMarketplaceController.class);
    private final ChannelDirectoryService channelDirectoryService;
    private final AuthorizationService authorizationService;

    public ScriptMarketplaceController(
        ChannelDirectoryService channelDirectoryService,
        AuthorizationService authorizationService
    ) {
        this.channelDirectoryService = channelDirectoryService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/scripts")
    public List<ScriptMarketplaceEntry> listMarketplaceScripts(
        @RequestParam(value = "query", required = false) String query,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = oauthToken == null ? null : OauthSessionUser.from(oauthToken).login();
        return channelDirectoryService.listMarketplaceScripts(query, sessionUsername);
    }

    @GetMapping("/scripts/{scriptId}/logo")
    public ResponseEntity<byte[]> getMarketplaceLogo(@PathVariable("scriptId") String scriptId) {
        String logScriptId = LogSanitizer.sanitize(scriptId);
        LOG.debug("Serving marketplace logo for script {}", logScriptId);
        return channelDirectoryService
            .getMarketplaceLogo(scriptId)
            .map((content) ->
                ResponseEntity.ok()
                    .header("X-Content-Type-Options", "nosniff")
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .contentType(MediaType.parseMediaType(content.mediaType()))
                    .body(content.bytes())
            )
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Logo not found"));
    }

    @PostMapping("/scripts/{scriptId}/import")
    public ResponseEntity<AssetView> importMarketplaceScript(
        @PathVariable("scriptId") String scriptId,
        @Valid @RequestBody ScriptMarketplaceImportRequest request,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(
            request.getTargetBroadcaster(),
            sessionUsername
        );
        String logScriptId = LogSanitizer.sanitize(scriptId);
        String logTarget = LogSanitizer.sanitize(request.getTargetBroadcaster());
        LOG.info("Importing marketplace script {} into {}", logScriptId, logTarget);
        return channelDirectoryService
            .importMarketplaceScript(request.getTargetBroadcaster(), scriptId)
            .map(ResponseEntity::ok)
            .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Unable to import script"));
    }

    @PostMapping("/scripts/{scriptId}/heart")
    public ResponseEntity<ScriptMarketplaceEntry> toggleMarketplaceHeart(
        @PathVariable("scriptId") String scriptId,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        return channelDirectoryService
            .toggleMarketplaceHeart(scriptId, sessionUsername)
            .map(ResponseEntity::ok)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Marketplace script not found"));
    }
}
