package dev.kruhlmann.imgfloat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.kruhlmann.imgfloat.model.db.imgfloat.SystemAdministrator;
import dev.kruhlmann.imgfloat.repository.SystemAdministratorRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

class SystemAdministratorServiceTest {

    private SystemAdministratorRepository repo;
    private Environment environment;
    private SystemAdministratorService service;

    @BeforeEach
    void setup() {
        repo = mock(SystemAdministratorRepository.class);
        environment = mock(Environment.class);
        when(environment.getProperty("IMGFLOAT_INITIAL_TWITCH_USERNAME_SYSADMIN")).thenReturn("admin");
        when(environment.getProperty("org.springframework.boot.test.context.SpringBootTestContextBootstrapper"))
            .thenReturn(null);
        service = new SystemAdministratorService(repo, environment);
    }

    @Test
    void isSysadminReturnsTrueForInitialSysadmin() {
        assertThat(service.isSysadmin("admin")).isTrue();
        assertThat(service.isSysadmin("ADMIN")).isTrue(); // case-insensitive
    }

    @Test
    void isSysadminDelegatesToRepositoryForOtherUsers() {
        when(repo.existsByTwitchUsername("other")).thenReturn(true);
        assertThat(service.isSysadmin("other")).isTrue();
        when(repo.existsByTwitchUsername("unknown")).thenReturn(false);
        assertThat(service.isSysadmin("unknown")).isFalse();
    }

    @Test
    void addSysadminSkipsIfAlreadyExists() {
        when(repo.existsByTwitchUsername("newuser")).thenReturn(true);
        service.addSysadmin("newuser");
        verify(repo, never()).save(any());
    }

    @Test
    void addSysadminSavesNewUser() {
        when(repo.existsByTwitchUsername("newuser")).thenReturn(false);
        service.addSysadmin("newuser");
        verify(repo).save(any(SystemAdministrator.class));
    }

    @Test
    void addSysadminThrowsForInitialSysadmin() {
        assertThatThrownBy(() -> service.addSysadmin("admin"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("initial");
    }

    @Test
    void removeSysadminThrowsForInitialSysadmin() {
        assertThatThrownBy(() -> service.removeSysadmin("admin"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("initial");
    }

    @Test
    void removeSysadminThrowsWhenUserDoesNotExist() {
        when(repo.deleteByTwitchUsername("nonexistent")).thenReturn(0L);
        assertThatThrownBy(() -> service.removeSysadmin("nonexistent"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removeSysadminDeletesExistingUser() {
        when(repo.deleteByTwitchUsername("user")).thenReturn(1L);
        service.removeSysadmin("user");
        verify(repo).deleteByTwitchUsername("user");
    }

    @Test
    void getInitialSysadminNormalizesUsername() {
        when(environment.getProperty("IMGFLOAT_INITIAL_TWITCH_USERNAME_SYSADMIN")).thenReturn("  AdminUser  ");
        assertThat(service.getInitialSysadmin()).isEqualTo("adminuser");
    }

    @Test
    void listSysadminsIncludesInitialSysadminFromEnvironment() {
        SystemAdministrator persisted = new SystemAdministrator("other");
        when(repo.findAllByOrderByTwitchUsernameAsc()).thenReturn(List.of(persisted));
        List<String> admins = service.listSysadmins();
        assertThat(admins).contains("admin", "other");
    }
}
