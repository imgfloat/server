package dev.kruhlmann.imgfloat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.List;

@Entity
@Table(name = "script_assets")
public class ScriptAsset {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "is_public")
    private boolean isPublic;

    private String mediaType;
    private String originalMediaType;

    @Column(name = "logo_file_id")
    private String logoFileId;

    @Column(name = "source_file_id")
    private String sourceFileId;

    @Column(name = "z_index")
    private Integer zIndex;

    @Transient
    private List<ScriptAssetAttachmentView> attachments = List.of();

    public ScriptAsset() {}

    public ScriptAsset(String assetId, String name) {
        this.id = assetId;
        this.name = name;
    }

    @PrePersist
    @PreUpdate
    public void prepare() {
        if (this.name == null || this.name.isBlank()) {
            this.name = this.id;
        }
        if (this.zIndex == null || this.zIndex < 1) {
            this.zIndex = 1;
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public String getOriginalMediaType() {
        return originalMediaType;
    }

    public void setOriginalMediaType(String originalMediaType) {
        this.originalMediaType = originalMediaType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    public String getLogoFileId() {
        return logoFileId;
    }

    public void setLogoFileId(String logoFileId) {
        this.logoFileId = logoFileId;
    }

    public String getSourceFileId() {
        return sourceFileId;
    }

    public void setSourceFileId(String sourceFileId) {
        this.sourceFileId = sourceFileId;
    }

    public Integer getZIndex() {
        return zIndex == null ? 1 : Math.max(1, zIndex);
    }

    public void setZIndex(Integer zIndex) {
        this.zIndex = zIndex == null ? null : Math.max(1, zIndex);
    }

    public List<ScriptAssetAttachmentView> getAttachments() {
        return attachments == null ? List.of() : attachments;
    }

    public void setAttachments(List<ScriptAssetAttachmentView> attachments) {
        this.attachments = attachments;
    }
}
