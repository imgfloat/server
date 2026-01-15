package dev.kruhlmann.imgfloat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "visual_assets")
public class VisualAsset {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    private String preview;

    private double x;
    private double y;
    private double width;
    private double height;
    private double rotation;
    private Double speed;
    private Boolean muted;
    private String mediaType;
    private String originalMediaType;
    private Double audioVolume;
    private boolean hidden;

    public VisualAsset() {}

    public VisualAsset(String assetId, String name, double width, double height) {
        this.id = assetId;
        this.name = name;
        this.width = width;
        this.height = height;
        this.x = 0;
        this.y = 0;
        this.rotation = 0;
        this.speed = 1.0;
        this.muted = false;
        this.audioVolume = 1.0;
        this.hidden = true;
    }

    @PrePersist
    @PreUpdate
    public void prepare() {
        if (this.speed == null) {
            this.speed = 1.0;
        }
        if (this.muted == null) {
            this.muted = Boolean.FALSE;
        }
        if (this.audioVolume == null) {
            this.audioVolume = 1.0;
        }
        if (this.name == null || this.name.isBlank()) {
            this.name = this.id;
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

    public String getPreview() {
        return preview;
    }

    public void setPreview(String preview) {
        this.preview = preview;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = width;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
    }

    public double getRotation() {
        return rotation;
    }

    public void setRotation(double rotation) {
        this.rotation = rotation;
    }

    public double getSpeed() {
        return speed == null ? 1.0 : speed;
    }

    public void setSpeed(Double speed) {
        this.speed = speed;
    }

    public boolean isMuted() {
        return muted != null && muted;
    }

    public void setMuted(Boolean muted) {
        this.muted = muted;
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

    public double getAudioVolume() {
        return audioVolume == null ? 1.0 : Math.max(0.0, Math.min(1.0, audioVolume));
    }

    public void setAudioVolume(Double audioVolume) {
        this.audioVolume = audioVolume;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }
}
