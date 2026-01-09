package dev.kruhlmann.imgfloat.service;

import dev.kruhlmann.imgfloat.model.SystemAdministrator;
import dev.kruhlmann.imgfloat.repository.SystemAdministratorRepository;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class SystemAdministratorService {

    private static final Logger logger = LoggerFactory.getLogger(SystemAdministratorService.class);

    private final SystemAdministratorRepository repo;
    private final Environment environment;

    public SystemAdministratorService(
        SystemAdministratorRepository repo,
        Environment environment
    ) {
        this.repo = repo;
        this.environment = environment;
    }

    @PostConstruct
    public void initDefaults() {
        if (
            Boolean.parseBoolean(
                environment.getProperty("org.springframework.boot.test.context.SpringBootTestContextBootstrapper")
            )
        ) {
            logger.info("Skipping system administrator bootstrap in test context");
            return;
        }

        String initialSysadmin = getInitialSysadmin();
        if (initialSysadmin != null) {
            long deleted = repo.deleteByTwitchUsername(initialSysadmin);
            if (deleted > 0) {
                logger.info("Removed persisted initial system administrator '{}' to use environment value", initialSysadmin);
            }
        }

        if (repo.count() > 0) {
            return;
        }

        if (initialSysadmin == null || initialSysadmin.isBlank()) {
            throw new IllegalStateException(
                "No system administrators exist and IMGFLOAT_INITIAL_TWITCH_USERNAME_SYSADMIN is not set"
            );
        }

        logger.info("Using initial system administrator '{}' from environment", initialSysadmin);
    }

    public void addSysadmin(String twitchUsername) {
        String normalized = normalize(twitchUsername);
        String initialSysadmin = getInitialSysadmin();

        if (initialSysadmin != null && initialSysadmin.equals(normalized)) {
            return;
        }

        if (repo.existsByTwitchUsername(normalized)) {
            return;
        }

        repo.save(new SystemAdministrator(normalized));
    }

    public void removeSysadmin(String twitchUsername) {
        String normalized = normalize(twitchUsername);
        String initialSysadmin = getInitialSysadmin();

        if (initialSysadmin != null && initialSysadmin.equals(normalized)) {
            throw new IllegalStateException("Cannot remove the initial system administrator");
        }

        if (initialSysadmin == null && repo.count() <= 1) {
            throw new IllegalStateException("Cannot remove the last system administrator");
        }

        long deleted = repo.deleteByTwitchUsername(normalized);

        if (deleted == 0) {
            throw new IllegalArgumentException("System administrator does not exist");
        }
    }

    public boolean isSysadmin(String twitchUsername) {
        String normalized = normalize(twitchUsername);
        String initialSysadmin = getInitialSysadmin();
        if (initialSysadmin != null && initialSysadmin.equals(normalized)) {
            return true;
        }
        return repo.existsByTwitchUsername(normalized);
    }

    public String getInitialSysadmin() {
        String value = environment.getProperty("IMGFLOAT_INITIAL_TWITCH_USERNAME_SYSADMIN");
        if (value == null || value.isBlank()) {
            return null;
        }
        return normalize(value);
    }

    public List<String> listSysadmins() {
        return repo
            .findAllByOrderByTwitchUsernameAsc()
            .stream()
            .map(SystemAdministrator::getTwitchUsername)
            .collect(Collectors.toList());
    }

    private String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
