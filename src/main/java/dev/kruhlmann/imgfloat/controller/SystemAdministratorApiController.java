package dev.kruhlmann.imgfloat.controller;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import dev.kruhlmann.imgfloat.model.OauthSessionUser;
import dev.kruhlmann.imgfloat.service.AuthorizationService;
import dev.kruhlmann.imgfloat.service.SystemAdministratorService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/system-administrators")
@SecurityRequirement(name = "administrator")
public class SystemAdministratorApiController {

    private static final Logger LOG = LoggerFactory.getLogger(SystemAdministratorApiController.class);

    private final SystemAdministratorService systemAdministratorService;
    private final AuthorizationService authorizationService;

    public SystemAdministratorApiController(
        SystemAdministratorService systemAdministratorService,
        AuthorizationService authorizationService
    ) {
        this.systemAdministratorService = systemAdministratorService;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    public ResponseEntity<List<String>> listSystemAdministrators(OAuth2AuthenticationToken oauthToken) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userIsSystemAdministratorOrThrowHttpError(sessionUsername);
        return ResponseEntity.ok().body(systemAdministratorService.listSysadmins());
    }

    @PostMapping
    public ResponseEntity<List<String>> addSystemAdministrator(
        @RequestBody SystemAdministratorRequest request,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userIsSystemAdministratorOrThrowHttpError(sessionUsername);
        if (request == null || request.twitchUsername() == null || request.twitchUsername().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Username is required");
        }
        String username = request.twitchUsername().trim();
        systemAdministratorService.addSysadmin(username);
        LOG.info("System administrator added: {} (requested by {})", username, sessionUsername);
        return ResponseEntity.ok().body(systemAdministratorService.listSysadmins());
    }

    @DeleteMapping("/{twitchUsername}")
    public ResponseEntity<List<String>> removeSystemAdministrator(
        @PathVariable("twitchUsername") String twitchUsername,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userIsSystemAdministratorOrThrowHttpError(sessionUsername);
        try {
            systemAdministratorService.removeSysadmin(twitchUsername);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(BAD_REQUEST, e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(NOT_FOUND, e.getMessage(), e);
        }
        LOG.info("System administrator removed: {} (requested by {})", twitchUsername, sessionUsername);
        return ResponseEntity.ok().body(systemAdministratorService.listSysadmins());
    }

    private record SystemAdministratorRequest(String twitchUsername) {}
}
