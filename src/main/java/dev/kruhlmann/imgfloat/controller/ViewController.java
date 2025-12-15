package dev.kruhlmann.imgfloat.controller;

import dev.kruhlmann.imgfloat.service.ChannelDirectoryService;
import dev.kruhlmann.imgfloat.service.VersionService;
import dev.kruhlmann.imgfloat.service.SettingsService;
import dev.kruhlmann.imgfloat.service.AuthorizationService;
import dev.kruhlmann.imgfloat.model.Settings;
import dev.kruhlmann.imgfloat.model.OauthSessionUser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.unit.DataSize;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

@Controller
public class ViewController {
    private static final Logger LOG = LoggerFactory.getLogger(ViewController.class);
    private final ChannelDirectoryService channelDirectoryService;
    private final VersionService versionService;
    private final SettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final AuthorizationService authorizationService;

    @Autowired
    private long uploadLimitBytes;

    public ViewController(
        ChannelDirectoryService channelDirectoryService,
        VersionService versionService,
        SettingsService settingsService,
        ObjectMapper objectMapper,
        AuthorizationService authorizationService
    ) {
        this.channelDirectoryService = channelDirectoryService;
        this.versionService = versionService;
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
        this.authorizationService = authorizationService;
    }

    @org.springframework.web.bind.annotation.GetMapping("/")
    public String home(OAuth2AuthenticationToken oauthToken, Model model) {
        if (oauthToken != null) {
            String sessionUsername = OauthSessionUser.from(oauthToken).login();
            LOG.info("Rendering dashboard for {}", sessionUsername);
            model.addAttribute("username", sessionUsername);
            model.addAttribute("channel", sessionUsername);
            model.addAttribute("adminChannels", channelDirectoryService.adminChannelsFor(sessionUsername));
            return "dashboard";
        }
        model.addAttribute("version", versionService.getVersion());
        return "index";
    }

    @org.springframework.web.bind.annotation.GetMapping("/channels")
    public String channelDirectory() {
        LOG.info("Rendering channel directory");
        return "channels";
    }

    @org.springframework.web.bind.annotation.GetMapping("/view/{broadcaster}/admin")
    public String adminView(@org.springframework.web.bind.annotation.PathVariable("broadcaster") String broadcaster,
                            OAuth2AuthenticationToken oauthToken,
                            Model model) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(broadcaster, sessionUsername);
        LOG.info("Rendering admin console for {} (requested by {})", broadcaster, sessionUsername);
        Settings settings = settingsService.get();
        model.addAttribute("broadcaster", broadcaster.toLowerCase());
        model.addAttribute("username", sessionUsername);
        model.addAttribute("uploadLimitBytes", uploadLimitBytes);
        try {
            model.addAttribute("settingsJson", objectMapper.writeValueAsString(settings));
        } catch (JsonProcessingException e) {
            LOG.error("Failed to serialize settings for admin view", e);
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Failed to serialize settings");
        }

        return "admin";
    }

    @org.springframework.web.bind.annotation.GetMapping("/view/{broadcaster}/broadcast")
    public String broadcastView(@org.springframework.web.bind.annotation.PathVariable("broadcaster") String broadcaster,
                                 Model model) {
        LOG.debug("Rendering broadcast overlay for {}", broadcaster);
        model.addAttribute("broadcaster", broadcaster.toLowerCase());
        return "broadcast";
    }
}
