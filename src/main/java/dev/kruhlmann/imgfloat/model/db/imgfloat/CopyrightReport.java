package dev.kruhlmann.imgfloat.model.db.imgfloat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "copyright_reports")
public class CopyrightReport {

    @Id
    private String id;

    @Column(name = "asset_id", nullable = false)
    private String assetId;

    @Column(nullable = false)
    private String broadcaster;

    @Column(name = "claimant_name", nullable = false)
    private String claimantName;

    @Column(name = "claimant_email", nullable = false)
    private String claimantEmail;

    @Column(name = "original_work_description", nullable = false)
    private String originalWorkDescription;

    @Column(name = "infringing_description", nullable = false)
    private String infringingDescription;

    @Column(name = "good_faith_declaration", nullable = false)
    private boolean goodFaithDeclaration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CopyrightReportStatus status = CopyrightReportStatus.PENDING;

    @Column(name = "resolution_notes")
    private String resolutionNotes;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CopyrightReport() {}

    @PrePersist
    public void prepare() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (status == null) {
            status = CopyrightReportStatus.PENDING;
        }
        if (broadcaster != null) {
            broadcaster = broadcaster.toLowerCase(Locale.ROOT);
        }
    }

    @PreUpdate
    public void touch() {
        updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }
    public String getBroadcaster() { return broadcaster; }
    public void setBroadcaster(String broadcaster) {
        this.broadcaster = broadcaster == null ? null : broadcaster.toLowerCase(Locale.ROOT);
    }
    public String getClaimantName() { return claimantName; }
    public void setClaimantName(String claimantName) { this.claimantName = claimantName; }
    public String getClaimantEmail() { return claimantEmail; }
    public void setClaimantEmail(String claimantEmail) { this.claimantEmail = claimantEmail; }
    public String getOriginalWorkDescription() { return originalWorkDescription; }
    public void setOriginalWorkDescription(String originalWorkDescription) {
        this.originalWorkDescription = originalWorkDescription;
    }
    public String getInfringingDescription() { return infringingDescription; }
    public void setInfringingDescription(String infringingDescription) {
        this.infringingDescription = infringingDescription;
    }
    public boolean isGoodFaithDeclaration() { return goodFaithDeclaration; }
    public void setGoodFaithDeclaration(boolean goodFaithDeclaration) {
        this.goodFaithDeclaration = goodFaithDeclaration;
    }
    public CopyrightReportStatus getStatus() { return status; }
    public void setStatus(CopyrightReportStatus status) { this.status = status; }
    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }
    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
