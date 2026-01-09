package dev.kruhlmann.imgfloat.service;

import dev.kruhlmann.imgfloat.model.SystemAdministrator;
import dev.kruhlmann.imgfloat.repository.SystemAdministratorRepository;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class SystemAdministratorService {

    private static final Logger logger = LoggerFactory.getLogger(SystemAdministratorService.class);

    private final SystemAdministratorRepository repo;
    private final String initialSysadmin;
    private final Environment environment;

    public SystemAdministratorService(
        SystemAdministratorRepository repo,
        @Value("${IMGFLOAT_INITIAL_TWITCH_USERNAME_SYSADMIN:#{null}}") String initialSysadmin,
        Environment environment
    ) {
        this.repo = repo;
        this.initialSysadmin = initialSysadmin;
        this.environment = environment;
    }

    @PostConstruct
    public void initDefaults() {
        if (repo.count() > 0) {
            return;
        }

        if (
            Boolean.parseBoolean(
                environment.getProperty("org.springframework.boot.test.context.SpringBootTestContextBootstrapper")
            )
        ) {
            logger.info("Skipping system administrator bootstrap in test context");
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
            throw new IllegalStateException("Cannot remove the last system administrator");
        }

        long deleted = repo.deleteByTwitchUsername(normalize(twitchUsername));

        if (deleted == 0) {
            throw new IllegalArgumentException("System administrator does not exist");
        }
    }

    public boolean isSysadmin(String twitchUsername) {
        return repo.existsByTwitchUsername(normalize(twitchUsername));
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
