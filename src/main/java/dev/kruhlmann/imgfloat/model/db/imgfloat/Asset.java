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

import dev.kruhlmann.imgfloat.model.AssetType;

@Entity
@Table(name = "assets")
public class Asset {

    @Id
    private String id;

    @Column(nullable = false)
    private String broadcaster;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false)
    private AssetType assetType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "display_order")
    private Integer displayOrder;

    public Asset() {}

    public Asset(String broadcaster, AssetType assetType) {
        this.id = UUID.randomUUID().toString();
        this.broadcaster = normalize(broadcaster);
        this.assetType = assetType == null ? AssetType.OTHER : assetType;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PrePersist
    @PreUpdate
    public void prepare() {
        Instant now = Instant.now();
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
        this.broadcaster = normalize(broadcaster);
        if (this.assetType == null) {
            this.assetType = AssetType.OTHER;
        }
        if (this.displayOrder != null && this.displayOrder < 1) {
            this.displayOrder = 1;
        }
    }

    public String getId() {
        return id;
    }

    public String getBroadcaster() {
        return broadcaster;
    }

    public void setBroadcaster(String broadcaster) {
        this.broadcaster = normalize(broadcaster);
    }

    public AssetType getAssetType() {
        return assetType == null ? AssetType.OTHER : assetType;
    }

    public void setAssetType(AssetType assetType) {
        this.assetType = assetType == null ? AssetType.OTHER : assetType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    private static String normalize(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
