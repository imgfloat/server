package dev.kruhlmann.imgfloat.controller;

import dev.kruhlmann.imgfloat.service.ChannelDirectoryService;
import dev.kruhlmann.imgfloat.service.VersionService;
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

@Controller
public class ViewController {
    private static final Logger LOG = LoggerFactory.getLogger(ViewController.class);
    private final ChannelDirectoryService channelDirectoryService;
    private final VersionService versionService;

    @Autowired
    private long uploadLimitBytes;

    private double maxSpeed;
    private double minAudioSpeed;
    private double maxAudioSpeed;
    private double minAudioPitch;
    private double maxAudioPitch;
    private double maxAudioVolume;

    public ViewController(
        ChannelDirectoryService channelDirectoryService,
        VersionService versionService,
        @Value("${IMGFLOAT_MAX_SPEED}") double maxSpeed,
        @Value("${IMGFLOAT_MIN_AUDIO_SPEED}") double minAudioSpeed,
        @Value("${IMGFLOAT_MAX_AUDIO_SPEED}") double maxAudioSpeed,
        @Value("${IMGFLOAT_MIN_AUDIO_PITCH}") double minAudioPitch,
        @Value("${IMGFLOAT_MAX_AUDIO_PITCH}") double maxAudioPitch,
        @Value("${IMGFLOAT_MAX_AUDIO_VOLUME}") double maxAudioVolume
    ) {
        this.channelDirectoryService = channelDirectoryService;
        this.versionService = versionService;
        this.maxSpeed = maxSpeed;
        this.minAudioSpeed = minAudioSpeed;
        this.maxAudioSpeed = maxAudioSpeed;
        this.minAudioPitch = minAudioPitch;
        this.maxAudioPitch = maxAudioPitch;
        this.maxAudioVolume = maxAudioVolume;
    }

    @org.springframework.web.bind.annotation.GetMapping("/")
    public String home(OAuth2AuthenticationToken authentication, Model model) {
        if (authentication != null) {
            String login = TwitchUser.from(authentication).login();
            LOG.info("Rendering dashboard for {}", login);
            model.addAttribute("username", login);
            model.addAttribute("channel", login);
            model.addAttribute("adminChannels", channelDirectoryService.adminChannelsFor(login));
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
                             OAuth2AuthenticationToken authentication,
                             Model model) {
        String login = TwitchUser.from(authentication).login();
        if (!channelDirectoryService.isBroadcaster(broadcaster, login)
                && !channelDirectoryService.isAdmin(broadcaster, login)) {
            LOG.warn("Unauthorized admin console access attempt for {} by {}", broadcaster, login);
            throw new ResponseStatusException(FORBIDDEN, "Not authorized for admin tools");
        }
        LOG.info("Rendering admin console for {} (requested by {})", broadcaster, login);
        model.addAttribute("broadcaster", broadcaster.toLowerCase());
        model.addAttribute("username", login);
        model.addAttribute("uploadLimitBytes", uploadLimitBytes);

        model.addAttribute("maxSpeed", maxSpeed);
        model.addAttribute("minAudioSpeed", minAudioSpeed);
        model.addAttribute("maxAudioSpeed", maxAudioSpeed);
        model.addAttribute("minAudioPitch", minAudioPitch);
        model.addAttribute("maxAudioPitch", maxAudioPitch);
        model.addAttribute("maxAudioVolume", maxAudioVolume);

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

record TwitchUser(String login, String displayName) {
    static TwitchUser from(OAuth2AuthenticationToken authentication) {
        if (authentication == null) {
            throw new ResponseStatusException(FORBIDDEN, "Authentication required");
        }
        String login = authentication.getPrincipal().<String>getAttribute("preferred_username");
        if (login == null) {
            login = authentication.getPrincipal().<String>getAttribute("login");
        }
        if (login == null) {
            login = authentication.getPrincipal().getName();
        }
        String displayName = authentication.getPrincipal().<String>getAttribute("display_name");
        if (displayName == null) {
            displayName = login;
        }
        return new TwitchUser(login, displayName);
    }
}
