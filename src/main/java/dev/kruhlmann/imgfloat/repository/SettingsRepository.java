package dev.kruhlmann.imgfloat.repository;

import dev.kruhlmann.imgfloat.model.db.imgfloat.Settings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingsRepository extends JpaRepository<Settings, Integer> {}
