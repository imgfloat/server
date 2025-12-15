package dev.kruhlmann.imgfloat.service;

import dev.kruhlmann.imgfloat.model.OauthSessionUser;
import dev.kruhlmann.imgfloat.service.ChannelDirectoryService;
import dev.kruhlmann.imgfloat.service.SystemAdministratorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class AuthorizationService {
    private static final Logger LOG = LoggerFactory.getLogger(AuthorizationService.class);

    private final ChannelDirectoryService channelDirectoryService;
    private final SystemAdministratorService systemAdministratorService;

    public AuthorizationService(ChannelDirectoryService channelDirectoryService, SystemAdministratorService systemAdministratorService) {
        this.channelDirectoryService = channelDirectoryService;
        this.systemAdministratorService = systemAdministratorService;
    }
    
    public void userMatchesSessionUsernameOrThrowHttpError(String submittedUsername, String sessionUsername) {
        if (sessionUsername == null) {
            LOG.warn("Access denied for broadcaster-only action by unauthenticated user");
            throw new ResponseStatusException(UNAUTHORIZED, "You must be logged in to manage your channel");
        }
        if (submittedUsername == null) {
            LOG.warn("User match with oauth token failed: submitted username is null for user {}", sessionUsername);
            throw new ResponseStatusException(NOT_FOUND, "You can only manage your own channel");
        }
        if (!sessionUsername.equals(submittedUsername)) {
            LOG.warn("User match with oauth token failed: session user {} does not match submitted user {}", sessionUsername, submittedUsername);
            throw new ResponseStatusException(FORBIDDEN, "You are not this user");
        }
    }

    public void userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError(String broadcaster, String sessionUsername) {
        if (!userIsBroadcasterOrChannelAdminForBroadcaster(broadcaster, sessionUsername)) {
            LOG.warn("Access denied for broadcaster/admin-only action by user {} on broadcaster {}", sessionUsername, broadcaster);
            throw new ResponseStatusException(FORBIDDEN, "You do not have permission to manage this channel");
        }
    }

    public void userIsSystemAdministratorOrThrowHttpError(String sessionUsername) {
        if (!userIsSystemAdministrator(sessionUsername)) {
            LOG.warn("Access denied for system administrator-only action by user {}", sessionUsername);
            throw new ResponseStatusException(FORBIDDEN, "You do not have permission to perform this action");
        }
    }

    public boolean userIsBroadcaster(String a, String b) {
        if (a == null || b == null) {
            LOG.warn("Broadcaster check failed: one or both usernames are null (a: {}, b: {})", a, b);
            return false;
        }
        return a.equals(b);
    }

    public boolean userIsChannelAdminForBroadcaster(String broadcaster, String sessionUsername) {
        if (sessionUsername == null || broadcaster == null) {
            LOG.warn("Channel admin check failed: broadcaster or session username is null (broadcaster: {}, sessionUsername: {})", broadcaster, sessionUsername);
            return false;
        }
        return channelDirectoryService.isAdmin(broadcaster, sessionUsername);
    }

    public boolean userIsBroadcasterOrChannelAdminForBroadcaster(String broadcaster, String sessionUser) {
        return userIsBroadcaster(sessionUser, broadcaster) ||
               userIsChannelAdminForBroadcaster(sessionUser, broadcaster);
    }

    public boolean userIsSystemAdministrator(String sessionUsername) {
        if (sessionUsername == null) {
            LOG.warn("System administrator check failed: session username is null");
            return false;
        }
        return systemAdministratorService.isSysadmin(sessionUsername);
    }
}
