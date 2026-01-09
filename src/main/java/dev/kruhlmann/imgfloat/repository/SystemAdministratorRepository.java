package dev.kruhlmann.imgfloat.repository;

import dev.kruhlmann.imgfloat.model.SystemAdministrator;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemAdministratorRepository extends JpaRepository<SystemAdministrator, String> {
    boolean existsByTwitchUsername(String twitchUsername);
    long deleteByTwitchUsername(String twitchUsername);
}
