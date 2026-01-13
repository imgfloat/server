package dev.kruhlmann.imgfloat.service;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import dev.kruhlmann.imgfloat.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthorizationService {

    private static final Logger LOG = LoggerFactory.getLogger(AuthorizationService.class);

    private final ChannelDirectoryService channelDirectoryService;
    private final SystemAdministratorService systemAdministratorService;
    private final boolean sysadminChannelAccessEnabled;

    public AuthorizationService(
        ChannelDirectoryService channelDirectoryService,
        SystemAdministratorService systemAdministratorService,
        @Value("${IMGFLOAT_SYSADMIN_CHANNEL_ACCESS_ENABLED:true}") boolean sysadminChannelAccessEnabled
    ) {
        this.channelDirectoryService = channelDirectoryService;
        this.systemAdministratorService = systemAdministratorService;
        this.sysadminChannelAccessEnabled = sysadminChannelAccessEnabled;
    }

    public void userMatchesSessionUsernameOrThrowHttpError(String submittedUsername, String sessionUsername) {
        if (sessionUsername == null) {
            LOG.warn("Access denied for broadcaster-only action by unauthenticated user");
            throw new ResponseStatusException(UNAUTHORIZED, "You must be logged in to manage your channel");
        }
        String logSessionUsername = LogSanitizer.sanitize(sessionUsername);
        if (submittedUsername == null) {
            LOG.warn("User match with oauth token failed: submitted username is null for user {}", logSessionUsername);
            throw new ResponseStatusException(NOT_FOUND, "You can only manage your own channel");
        }
        String logSubmittedUsername = LogSanitizer.sanitize(submittedUsername);
        if (!sessionUsername.equals(submittedUsername)) {
            LOG.warn(
                "User match with oauth token failed: session user {} does not match submitted user {}",
                logSessionUsername,
                logSubmittedUsername
            );
            throw new ResponseStatusException(FORBIDDEN, "You are not this user");
        }
    }

    public void userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(
        String broadcaster,
        String sessionUsername
    ) {
        String logBroadcaster = LogSanitizer.sanitize(broadcaster);
        if (!userIsBroadcasterOrChannelAdminForBroadcaster(logBroadcaster, sessionUsername)) {
            LOG.warn(
                "Access denied for broadcaster/admin-only action by user {} on broadcaster {}",
                LogSanitizer.sanitize(sessionUsername),
                logBroadcaster
            );
            throw new ResponseStatusException(FORBIDDEN, "You do not have permission to manage this channel");
        }
    }

    public void userIsSystemAdministratorOrThrowHttpError(String sessionUsername) {
        if (!userIsSystemAdministrator(sessionUsername)) {
            LOG.warn(
                "Access denied for system administrator-only action by user {}",
                LogSanitizer.sanitize(sessionUsername)
            );
            throw new ResponseStatusException(FORBIDDEN, "You do not have permission to perform this action");
        }
    }

    public boolean userIsBroadcaster(String a, String b) {
        if (a == null || b == null) {
            LOG.warn(
                "Broadcaster check failed: one or both usernames are null (a: {}, b: {})",
                LogSanitizer.sanitize(a),
                LogSanitizer.sanitize(b)
            );
            return false;
        }
        return a.equals(b);
    }

    public boolean userIsChannelAdminForBroadcaster(String broadcaster, String sessionUsername) {
        if (sessionUsername == null || broadcaster == null) {
            LOG.warn(
                "Channel admin check failed: broadcaster or session username is null (broadcaster: {}, sessionUsername: {})",
                LogSanitizer.sanitize(broadcaster),
                LogSanitizer.sanitize(sessionUsername)
            );
            return false;
        }
        return channelDirectoryService.isAdmin(broadcaster, sessionUsername);
    }

    public boolean userIsBroadcasterOrChannelAdminForBroadcaster(String broadcaster, String sessionUser) {
        return (
            userIsBroadcaster(sessionUser, broadcaster)
                || userIsChannelAdminForBroadcaster(broadcaster, sessionUser)
                || (sysadminChannelAccessEnabled && userIsSystemAdministrator(sessionUser))
        );
    }

    public boolean userIsSystemAdministrator(String sessionUsername) {
        if (sessionUsername == null) {
            LOG.warn("System administrator check failed: session username is null");
            return false;
        }
        return systemAdministratorService.isSysadmin(sessionUsername);
    }
}
