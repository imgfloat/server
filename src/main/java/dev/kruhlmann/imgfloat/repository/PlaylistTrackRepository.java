package dev.kruhlmann.imgfloat.repository;

import dev.kruhlmann.imgfloat.model.db.imgfloat.PlaylistTrack;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaylistTrackRepository extends JpaRepository<PlaylistTrack, String> {
    List<PlaylistTrack> findAllByPlaylistIdOrderByTrackOrderAsc(String playlistId);
    void deleteAllByPlaylistId(String playlistId);
    int countByPlaylistId(String playlistId);
}
