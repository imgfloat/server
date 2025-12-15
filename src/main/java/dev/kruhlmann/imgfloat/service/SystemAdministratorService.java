package dev.kruhlmann.imgfloat.service;

import dev.kruhlmann.imgfloat.model.SystemAdministrator;
import dev.kruhlmann.imgfloat.repository.SystemAdministratorRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class SystemAdministratorService {

    private static final Logger logger =
            LoggerFactory.getLogger(SystemAdministratorService.class);

    private final SystemAdministratorRepository repo;
    private final String initialSysadmin;

    public SystemAdministratorService(
            SystemAdministratorRepository repo,
            @Value("${IMGFLOAT_INITIAL_TWITCH_USERNAME_SYSADMIN:#{null}}")
            String initialSysadmin
    ) {
        this.repo = repo;
        this.initialSysadmin = initialSysadmin;
    }

    @PostConstruct
    public void initDefaults() {
        if (repo.count() > 0) {
            return;
        }

        if (initialSysadmin == null || initialSysadmin.isBlank()) {
            throw new IllegalStateException(
                "No system administrators exist and IMGFLOAT_INITIAL_TWITCH_USERNAME_SYSADMIN is not set"
            );
        }

        addSysadmin(initialSysadmin);
        logger.info("Created initial system administrator '{}'", initialSysadmin);
    }

    public void addSysadmin(String twitchUsername) {
        String normalized = normalize(twitchUsername);

        if (repo.existsByTwitchUsername(normalized)) {
            return;
        }

        repo.save(new SystemAdministrator(normalized));
    }

    public void removeSysadmin(String twitchUsername) {
        if (repo.count() <= 1) {
            throw new IllegalStateException(
                "Cannot remove the last system administrator"
            );
        }

        long deleted = repo.deleteByTwitchUsername(normalize(twitchUsername));

        if (deleted == 0) {
            throw new IllegalArgumentException(
                "System administrator does not exist"
            );
        }
    }

    public boolean isSysadmin(String twitchUsername) {
        return repo.existsByTwitchUsername(normalize(twitchUsername));
    }

    private String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
