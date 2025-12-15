package dev.kruhlmann.imgfloat.repository;

import dev.kruhlmann.imgfloat.model.Settings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingsRepository extends JpaRepository<Settings, Integer> {
}
