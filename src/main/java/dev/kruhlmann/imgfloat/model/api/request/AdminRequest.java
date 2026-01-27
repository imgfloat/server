package dev.kruhlmann.imgfloat.model.api.request;

import jakarta.validation.constraints.NotBlank;

public record AdminRequest(@NotBlank String username) {}