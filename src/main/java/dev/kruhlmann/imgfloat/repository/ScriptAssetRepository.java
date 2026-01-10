package dev.kruhlmann.imgfloat.repository;

import dev.kruhlmann.imgfloat.model.ScriptAsset;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScriptAssetRepository extends JpaRepository<ScriptAsset, String> {
    List<ScriptAsset> findByIdIn(Collection<String> ids);

    List<ScriptAsset> findByIsPublicTrue();

    long countBySourceFileId(String sourceFileId);

    long countByLogoFileId(String logoFileId);
}
