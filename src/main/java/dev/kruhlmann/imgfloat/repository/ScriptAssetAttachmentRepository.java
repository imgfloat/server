package dev.kruhlmann.imgfloat.repository;

import dev.kruhlmann.imgfloat.model.ScriptAssetAttachment;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScriptAssetAttachmentRepository extends JpaRepository<ScriptAssetAttachment, String> {
    List<ScriptAssetAttachment> findByScriptAssetId(String scriptAssetId);

    List<ScriptAssetAttachment> findByScriptAssetIdIn(Collection<String> scriptAssetIds);

    void deleteByScriptAssetId(String scriptAssetId);

    long countByFileId(String fileId);
}
