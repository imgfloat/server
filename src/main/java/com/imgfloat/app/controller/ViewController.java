package com.imgfloat.app.controller;

import com.imgfloat.app.service.ChannelDirectoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.FORBIDDEN;

@Controller
public class ViewController {
    private static final Logger LOG = LoggerFactory.getLogger(ViewController.class);
    private final ChannelDirectoryService channelDirectoryService;

    public ViewController(ChannelDirectoryService channelDirectoryService) {
        this.channelDirectoryService = channelDirectoryService;
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
        return "index";
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
