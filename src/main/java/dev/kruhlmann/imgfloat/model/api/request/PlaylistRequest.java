package dev.kruhlmann.imgfloat.model.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlaylistRequest(
    @NotBlank @Size(max = 100) String name
) {}
