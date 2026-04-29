package dev.kruhlmann.imgfloat.repository;

import dev.kruhlmann.imgfloat.model.db.imgfloat.Playlist;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaylistRepository extends JpaRepository<Playlist, String> {
    List<Playlist> findAllByBroadcasterOrderByCreatedAtAsc(String broadcaster);
    Optional<Playlist> findByIdAndBroadcaster(String id, String broadcaster);
}
