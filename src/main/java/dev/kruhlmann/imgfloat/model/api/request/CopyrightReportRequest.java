package dev.kruhlmann.imgfloat.model.api.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CopyrightReportRequest(
    @NotBlank(message = "Claimant name is required")
    @Size(max = 255, message = "Claimant name must be 255 characters or fewer")
    String claimantName,

    @NotBlank(message = "Claimant email is required")
    @Email(message = "Claimant email must be a valid email address")
    @Size(max = 255, message = "Claimant email must be 255 characters or fewer")
    String claimantEmail,

    @NotBlank(message = "Original work description is required")
    @Size(max = 4000, message = "Original work description must be 4000 characters or fewer")
    String originalWorkDescription,

    @NotBlank(message = "Description of infringement is required")
    @Size(max = 4000, message = "Description of infringement must be 4000 characters or fewer")
    String infringingDescription,

    @NotNull(message = "Good faith declaration is required")
    Boolean goodFaithDeclaration
) {}
