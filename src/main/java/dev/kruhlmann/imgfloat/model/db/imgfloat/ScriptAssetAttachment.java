package dev.kruhlmann.imgfloat.model.db.imgfloat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.util.UUID;

import dev.kruhlmann.imgfloat.model.AssetType;

@Entity
@Table(name = "script_asset_attachments")
public class ScriptAssetAttachment {

    @Id
    private String id;

    @Column(name = "script_asset_id", nullable = false)
    private String scriptAssetId;

    @Column(name = "file_id")
    private String fileId;

    @Column(nullable = false)
    private String name;

    private String mediaType;
    private String originalMediaType;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false)
    private AssetType assetType;

    public ScriptAssetAttachment() {}

    public ScriptAssetAttachment(String scriptAssetId, String name) {
        this.id = UUID.randomUUID().toString();
        this.scriptAssetId = scriptAssetId;
        this.name = name;
        this.assetType = AssetType.OTHER;
    }

    @PrePersist
    @PreUpdate
    public void prepare() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.name == null || this.name.isBlank()) {
            this.name = this.id;
        }
        if (this.assetType == null) {
            this.assetType = AssetType.OTHER;
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getScriptAssetId() {
        return scriptAssetId;
    }

    public void setScriptAssetId(String scriptAssetId) {
        this.scriptAssetId = scriptAssetId;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
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

    public AssetType getAssetType() {
        return assetType == null ? AssetType.OTHER : assetType;
    }

    public void setAssetType(AssetType assetType) {
        this.assetType = assetType == null ? AssetType.OTHER : assetType;
    }
}
