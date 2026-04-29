package dev.kruhlmann.imgfloat.model.api.request;

import jakarta.validation.constraints.NotBlank;

public record PlaylistTrackRequest(
    @NotBlank String audioAssetId
) {}
