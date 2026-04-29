package dev.kruhlmann.imgfloat.model.api.response;

import java.util.List;

public record PlaylistTrackView(
    String id,
    String audioAssetId,
    String assetName,
    int trackOrder
) {}
