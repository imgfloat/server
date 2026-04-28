package dev.kruhlmann.imgfloat.model.api.response;

import dev.kruhlmann.imgfloat.model.db.imgfloat.CopyrightReport;
import dev.kruhlmann.imgfloat.model.db.imgfloat.CopyrightReportStatus;
import java.time.Instant;

public record CopyrightReportView(
    String id,
    String assetId,
    String broadcaster,
    String claimantName,
    String claimantEmail,
    String originalWorkDescription,
    String infringingDescription,
    boolean goodFaithDeclaration,
    CopyrightReportStatus status,
    String resolutionNotes,
    String resolvedBy,
    Instant createdAt,
    Instant updatedAt
) {
    public static CopyrightReportView fromReport(CopyrightReport report) {
        if (report == null) {
            return null;
        }
        return new CopyrightReportView(
            report.getId(),
            report.getAssetId(),
            report.getBroadcaster(),
            report.getClaimantName(),
            report.getClaimantEmail(),
            report.getOriginalWorkDescription(),
            report.getInfringingDescription(),
            report.isGoodFaithDeclaration(),
            report.getStatus(),
            report.getResolutionNotes(),
            report.getResolvedBy(),
            report.getCreatedAt(),
            report.getUpdatedAt()
        );
    }
}
