package dev.kruhlmann.imgfloat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AuthorizationServiceTest {

    private ChannelDirectoryService channelDirectoryService;
    private SystemAdministratorService sysadminService;
    private AuthorizationService authorizationService;
    private AuthorizationService authorizationServiceSysadminDisabled;

    @BeforeEach
    void setup() {
        channelDirectoryService = mock(ChannelDirectoryService.class);
        sysadminService = mock(SystemAdministratorService.class);
        authorizationService = new AuthorizationService(channelDirectoryService, sysadminService, true);
        authorizationServiceSysadminDisabled = new AuthorizationService(channelDirectoryService, sysadminService, false);
    }

    // --- userMatchesSessionUsernameOrThrowHttpError ---

    @Test
    void matchesSessionUsernamePassesWhenEqual() {
        // must not throw
        authorizationService.userMatchesSessionUsernameOrThrowHttpError("alice", "alice");
    }

    @Test
    void matchesSessionUsernameThrowsWhenSessionNull() {
        assertThatThrownBy(() -> authorizationService.userMatchesSessionUsernameOrThrowHttpError("alice", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("logged in");
    }

    @Test
    void matchesSessionUsernameThrowsWhenSubmittedNull() {
        assertThatThrownBy(() -> authorizationService.userMatchesSessionUsernameOrThrowHttpError(null, "alice"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void matchesSessionUsernameThrowsWhenDifferent() {
        assertThatThrownBy(() -> authorizationService.userMatchesSessionUsernameOrThrowHttpError("alice", "bob"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not this user");
    }

    // --- userIsBroadcaster ---

    @Test
    void userIsBroadcasterReturnsTrueWhenEqual() {
        assertThat(authorizationService.userIsBroadcaster("alice", "alice")).isTrue();
    }

    @Test
    void userIsBroadcasterReturnsFalseWhenDifferent() {
        assertThat(authorizationService.userIsBroadcaster("alice", "bob")).isFalse();
    }

    @Test
    void userIsBroadcasterReturnsFalseWhenEitherNull() {
        assertThat(authorizationService.userIsBroadcaster(null, "alice")).isFalse();
        assertThat(authorizationService.userIsBroadcaster("alice", null)).isFalse();
    }

    // --- userIsChannelAdminForBroadcaster ---

    @Test
    void channelAdminCheckDelegatesToChannelDirectoryService() {
        when(channelDirectoryService.isAdmin("broadcaster", "alice")).thenReturn(true);
        assertThat(authorizationService.userIsChannelAdminForBroadcaster("broadcaster", "alice")).isTrue();
    }

    @Test
    void channelAdminCheckReturnsFalseWhenNull() {
        assertThat(authorizationService.userIsChannelAdminForBroadcaster(null, "alice")).isFalse();
        assertThat(authorizationService.userIsChannelAdminForBroadcaster("broadcaster", null)).isFalse();
    }

    // --- userIsSystemAdministrator ---

    @Test
    void sysadminCheckDelegatesToSysadminService() {
        when(sysadminService.isSysadmin("admin")).thenReturn(true);
        assertThat(authorizationService.userIsSystemAdministrator("admin")).isTrue();
    }

    @Test
    void sysadminCheckReturnsFalseWhenNull() {
        assertThat(authorizationService.userIsSystemAdministrator(null)).isFalse();
    }

    // --- userIsBroadcasterOrChannelAdmin ---

    @Test
    void allowsWhenBroadcasterMatchesSelf() {
        assertThat(authorizationService.userIsBroadcasterOrChannelAdminForBroadcaster("alice", "alice")).isTrue();
    }

    @Test
    void allowsChannelAdmin() {
        when(channelDirectoryService.isAdmin("broadcaster", "admin")).thenReturn(true);
        assertThat(authorizationService.userIsBroadcasterOrChannelAdminForBroadcaster("broadcaster", "admin")).isTrue();
    }

    @Test
    void allowsSysadminWhenAccessEnabled() {
        when(sysadminService.isSysadmin("sysadmin")).thenReturn(true);
        assertThat(authorizationService.userIsBroadcasterOrChannelAdminForBroadcaster("broadcaster", "sysadmin")).isTrue();
    }

    @Test
    void deniedSysadminWhenAccessDisabled() {
        when(sysadminService.isSysadmin("sysadmin")).thenReturn(true);
        when(channelDirectoryService.isAdmin("broadcaster", "sysadmin")).thenReturn(false);
        assertThat(authorizationServiceSysadminDisabled.userIsBroadcasterOrChannelAdminForBroadcaster("broadcaster", "sysadmin")).isFalse();
    }

    @Test
    void deniesRandomUser() {
        when(channelDirectoryService.isAdmin("broadcaster", "random")).thenReturn(false);
        when(sysadminService.isSysadmin("random")).thenReturn(false);
        assertThat(authorizationService.userIsBroadcasterOrChannelAdminForBroadcaster("broadcaster", "random")).isFalse();
    }

    // --- throw-based wrappers ---

    @Test
    void throwsWhenNotBroadcasterOrAdmin() {
        when(channelDirectoryService.isAdmin("broadcaster", "random")).thenReturn(false);
        when(sysadminService.isSysadmin("random")).thenReturn(false);
        assertThatThrownBy(
            () -> authorizationService.userIsBroadcasterOrChannelAdminForBroadcasterOrThrowHttpError("broadcaster", "random")
        ).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void throwsWhenNotSysadmin() {
        when(sysadminService.isSysadmin("user")).thenReturn(false);
        assertThatThrownBy(() -> authorizationService.userIsSystemAdministratorOrThrowHttpError("user"))
                .isInstanceOf(ResponseStatusException.class);
    }
}
