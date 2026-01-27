package dev.kruhlmann.imgfloat.repository;

import dev.kruhlmann.imgfloat.model.db.imgfloat.ScriptAssetFile;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScriptAssetFileRepository extends JpaRepository<ScriptAssetFile, String> {
    List<ScriptAssetFile> findByBroadcaster(String broadcaster);
}
