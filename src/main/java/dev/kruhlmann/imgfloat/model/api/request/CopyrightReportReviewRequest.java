package dev.kruhlmann.imgfloat.model.api.request;

import dev.kruhlmann.imgfloat.service.CopyrightReportAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CopyrightReportReviewRequest(
    @NotNull(message = "Action is required")
    CopyrightReportAction action,

    @Size(max = 4000, message = "Resolution notes must be 4000 characters or fewer")
    String resolutionNotes
) {}
