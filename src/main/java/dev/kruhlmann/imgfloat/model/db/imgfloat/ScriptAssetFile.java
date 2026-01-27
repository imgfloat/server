package dev.kruhlmann.imgfloat.model.db.imgfloat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.UUID;

import dev.kruhlmann.imgfloat.model.AssetType;

@Entity
@Table(name = "script_asset_files")
public class ScriptAssetFile {

    @Id
    private String id;

    @Column(nullable = false)
    private String broadcaster;

    private String mediaType;
    private String originalMediaType;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false)
    private AssetType assetType;

    public ScriptAssetFile() {}

    public ScriptAssetFile(String broadcaster, AssetType assetType) {
        this.id = UUID.randomUUID().toString();
        this.broadcaster = normalize(broadcaster);
        this.assetType = assetType == null ? AssetType.OTHER : assetType;
    }

    @PrePersist
    @PreUpdate
    public void prepare() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        this.broadcaster = normalize(broadcaster);
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

    public String getBroadcaster() {
        return broadcaster;
    }

    public void setBroadcaster(String broadcaster) {
        this.broadcaster = normalize(broadcaster);
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

    private static String normalize(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
