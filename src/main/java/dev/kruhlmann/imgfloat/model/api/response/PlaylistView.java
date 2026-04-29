package dev.kruhlmann.imgfloat.model.api.response;

import java.util.List;

public record PlaylistView(
    String id,
    String name,
    List<PlaylistTrackView> tracks
) {}
