package dev.kruhlmann.imgfloat.repository;

import dev.kruhlmann.imgfloat.model.db.imgfloat.AudioAsset;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AudioAssetRepository extends JpaRepository<AudioAsset, String> {
    List<AudioAsset> findByIdIn(Collection<String> ids);
}
