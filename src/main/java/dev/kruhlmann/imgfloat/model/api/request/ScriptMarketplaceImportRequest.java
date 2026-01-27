package dev.kruhlmann.imgfloat.model.api.request;

import jakarta.validation.constraints.NotBlank;

public record ScriptMarketplaceImportRequest(@NotBlank String targetBroadcaster) { }
