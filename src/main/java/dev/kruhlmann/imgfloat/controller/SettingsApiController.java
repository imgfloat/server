package dev.kruhlmann.imgfloat.controller;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import dev.kruhlmann.imgfloat.model.OauthSessionUser;
import dev.kruhlmann.imgfloat.model.Settings;
import dev.kruhlmann.imgfloat.service.AuthorizationService;
import dev.kruhlmann.imgfloat.service.SettingsService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/settings")
@SecurityRequirement(name = "administrator")
public class SettingsApiController {

    private static final Logger LOG = LoggerFactory.getLogger(SettingsApiController.class);

    private final SettingsService settingsService;
    private final AuthorizationService authorizationService;

    public SettingsApiController(SettingsService settingsService, AuthorizationService authorizationService) {
        this.settingsService = settingsService;
        this.authorizationService = authorizationService;
    }

    @PutMapping("/set")
    public ResponseEntity<Settings> setSettings(
        @Valid @RequestBody Settings newSettings,
        OAuth2AuthenticationToken oauthToken
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userIsSystemAdministratorOrThrowHttpError(sessionUsername);

        Settings currentSettings = settingsService.get();
        LOG.info("Sytem administrator settings change request");
        settingsService.logSettings("From: ", currentSettings);
        settingsService.logSettings("To: ", newSettings);

        return ResponseEntity.ok().body(newSettings);
    }
}
