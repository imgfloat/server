package dev.kruhlmann.imgfloat.model.api.request;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record PlaylistTrackOrderRequest(
    @NotNull List<String> trackIds
) {}
