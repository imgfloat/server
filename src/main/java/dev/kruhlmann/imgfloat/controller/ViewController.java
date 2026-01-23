package dev.kruhlmann.imgfloat.controller;

import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kruhlmann.imgfloat.model.OauthSessionUser;
import dev.kruhlmann.imgfloat.model.Settings;
import dev.kruhlmann.imgfloat.service.AuthorizationService;
import dev.kruhlmann.imgfloat.service.ChannelDirectoryService;
import dev.kruhlmann.imgfloat.service.GitInfoService;
import dev.kruhlmann.imgfloat.service.GithubReleaseService;
import dev.kruhlmann.imgfloat.service.SettingsService;
import dev.kruhlmann.imgfloat.service.SystemAdministratorService;
import dev.kruhlmann.imgfloat.service.VersionService;
import dev.kruhlmann.imgfloat.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class ViewController {

    private static final Logger LOG = LoggerFactory.getLogger(ViewController.class);
    private final ChannelDirectoryService channelDirectoryService;
    private final VersionService versionService;
    private final SettingsService settingsService;
    private final GitInfoService gitInfoService;
    private final ObjectMapper objectMapper;
    private final AuthorizationService authorizationService;
    private final GithubReleaseService githubReleaseService;
    private final SystemAdministratorService systemAdministratorService;
    private final long uploadLimitBytes;
    private final boolean isStaging;
    private final String docsUrl;

    public ViewController(
        ChannelDirectoryService channelDirectoryService,
        VersionService versionService,
        SettingsService settingsService,
        GitInfoService gitInfoService,
        ObjectMapper objectMapper,
        AuthorizationService authorizationService,
        GithubReleaseService githubReleaseService,
        SystemAdministratorService systemAdministratorService,
        long uploadLimitBytes,
        @Value("${IMGFLOAT_IS_STAGING:0}") String isStagingFlag,
        @Value("${IMGFLOAT_DOCS_URL:https://docs.imgflo.at}") String docsUrl
    ) {
        this.channelDirectoryService = channelDirectoryService;
        this.versionService = versionService;
        this.settingsService = settingsService;
        this.gitInfoService = gitInfoService;
        this.objectMapper = objectMapper;
        this.authorizationService = authorizationService;
        this.githubReleaseService = githubReleaseService;
        this.systemAdministratorService = systemAdministratorService;
        this.uploadLimitBytes = uploadLimitBytes;
        this.isStaging = "1".equals(isStagingFlag);
        this.docsUrl = docsUrl;
    }

    @org.springframework.web.bind.annotation.GetMapping("/")
    public String home(OAuth2AuthenticationToken oauthToken, Model model) {
        if (oauthToken != null) {
            String sessionUsername = OauthSessionUser.from(oauthToken).login();
            String logSessionUsername = LogSanitizer.sanitize(sessionUsername);
            LOG.info("Rendering dashboard for {}", logSessionUsername);
            model.addAttribute("username", sessionUsername);
            model.addAttribute("channel", sessionUsername);
            model.addAttribute("adminChannels", channelDirectoryService.adminChannelsFor(sessionUsername));
            model.addAttribute("isSystemAdmin", authorizationService.userIsSystemAdministrator(sessionUsername));
            addStagingAttribute(model);
            addVersionAttributes(model);
            return "dashboard";
        }
        addStagingAttribute(model);
        addVersionAttributes(model);
        return "index";
    }

    @org.springframework.web.bind.annotation.GetMapping("/channels")
    public String channelDirectory(Model model) {
        LOG.info("Rendering channel directory");
        addStagingAttribute(model);
        addVersionAttributes(model);
        return "channels";
    }

    @org.springframework.web.bind.annotation.GetMapping("/terms")
    public String termsOfUse(Model model) {
        LOG.info("Rendering terms of use");
        addStagingAttribute(model);
        addVersionAttributes(model);
        return "terms";
    }

    @org.springframework.web.bind.annotation.GetMapping("/privacy")
    public String privacyPolicy(Model model) {
        LOG.info("Rendering privacy policy");
        addStagingAttribute(model);
        addVersionAttributes(model);
        return "privacy";
    }

    @org.springframework.web.bind.annotation.GetMapping("/cookies")
    public String cookiePolicy(Model model) {
        LOG.info("Rendering cookie policy");
        addStagingAttribute(model);
        addVersionAttributes(model);
        return "cookies";
    }

    @org.springframework.web.bind.annotation.GetMapping("/settings")
    public String settingsView(OAuth2AuthenticationToken oauthToken, Model model) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userIsSystemAdministratorOrThrowHttpError(sessionUsername);
        String logSessionUsername = LogSanitizer.sanitize(sessionUsername);
        LOG.info("Rendering settings for {}", logSessionUsername);
        Settings settings = settingsService.get();
        try {
            model.addAttribute("settingsJson", objectMapper.writeValueAsString(settings));
        } catch (JsonProcessingException e) {
            LOG.error("Failed to serialize settings for settings view", e);
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Failed to serialize settings");
        }
        model.addAttribute("initialSysadmin", systemAdministratorService.getInitialSysadmin());
        addStagingAttribute(model);
        addDocsAttribute(model);
        return "settings";
    }

    @org.springframework.web.bind.annotation.GetMapping("/view/{broadcaster}/admin")
    public String adminView(
        @org.springframework.web.bind.annotation.PathVariable("broadcaster") String broadcaster,
        OAuth2AuthenticationToken oauthToken,
        Model model
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(
            broadcaster,
            sessionUsername
        );
        String logBroadcaster = LogSanitizer.sanitize(broadcaster);
        String logSessionUsername = LogSanitizer.sanitize(sessionUsername);
        LOG.info("Rendering admin console for {} (requested by {})", logBroadcaster, logSessionUsername);
        Settings settings = settingsService.get();
        model.addAttribute("broadcaster", broadcaster.toLowerCase());
        model.addAttribute("username", sessionUsername);
        model.addAttribute("adminChannels", channelDirectoryService.adminChannelsFor(sessionUsername));
        model.addAttribute("uploadLimitBytes", uploadLimitBytes);
        try {
            model.addAttribute("settingsJson", objectMapper.writeValueAsString(settings));
        } catch (JsonProcessingException e) {
            LOG.error("Failed to serialize settings for admin view", e);
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Failed to serialize settings");
        }
        addStagingAttribute(model);
        addDocsAttribute(model);

        return "admin";
    }

    @org.springframework.web.bind.annotation.GetMapping("/view/{broadcaster}/audit")
    public String auditLogView(
        @org.springframework.web.bind.annotation.PathVariable("broadcaster") String broadcaster,
        OAuth2AuthenticationToken oauthToken,
        Model model
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        authorizationService.userMatchesSessionUsernameOrThrowHttpError(broadcaster, sessionUsername);
        String logBroadcaster = LogSanitizer.sanitize(broadcaster);
        String logSessionUsername = LogSanitizer.sanitize(sessionUsername);
        LOG.info("Rendering audit log for {} by {}", logBroadcaster, logSessionUsername);
        model.addAttribute("broadcaster", broadcaster.toLowerCase());
        model.addAttribute("username", sessionUsername);
        addStagingAttribute(model);
        addVersionAttributes(model);
        return "audit-log";
    }

    @org.springframework.web.bind.annotation.GetMapping("/view/{broadcaster}/broadcast")
    public String broadcastView(
        @org.springframework.web.bind.annotation.PathVariable("broadcaster") String broadcaster,
        Model model
    ) {
        String logBroadcaster = LogSanitizer.sanitize(broadcaster);
        LOG.debug("Rendering broadcast overlay for {}", logBroadcaster);
        model.addAttribute("broadcaster", broadcaster.toLowerCase());
        return "broadcast";
    }

    private void addVersionAttributes(Model model) {
        model.addAttribute("version", versionService.getVersion());
        model.addAttribute("releaseVersion", githubReleaseService.getClientReleaseVersion());
        model.addAttribute("downloadBaseUrl", githubReleaseService.getDownloadBaseUrl());
        model.addAttribute("showCommitChip", gitInfoService.shouldShowCommitChip());
        model.addAttribute("buildCommitShort", gitInfoService.getShortCommitSha());
        model.addAttribute("buildCommitUrl", gitInfoService.getCommitUrl());
        addDocsAttribute(model);
    }

    private void addStagingAttribute(Model model) {
        model.addAttribute("isStaging", isStaging);
    }

    private void addDocsAttribute(Model model) {
        model.addAttribute("docsUrl", docsUrl);
    }
}
