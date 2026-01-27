package dev.kruhlmann.imgfloat.repository;

import dev.kruhlmann.imgfloat.model.db.imgfloat.VisualAsset;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VisualAssetRepository extends JpaRepository<VisualAsset, String> {
    List<VisualAsset> findByIdIn(Collection<String> ids);
    List<VisualAsset> findByIdInAndHiddenFalse(Collection<String> ids);
}
