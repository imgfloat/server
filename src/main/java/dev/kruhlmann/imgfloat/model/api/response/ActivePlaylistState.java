package dev.kruhlmann.imgfloat.model.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Returned by GET /api/channels/{broadcaster}/playlists/active.
 * Extends the basic PlaylistView with persisted playback state so that
 * reconnecting clients (broadcast view, admin view) can resume correctly.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ActivePlaylistState(
    String id,
    String name,
    List<PlaylistTrackView> tracks,
    String currentTrackId,
    boolean isPlaying,
    boolean isPaused,
    double trackPosition
) {}
