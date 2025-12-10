package com.imgfloat.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.PreUpdate;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "assets")
public class Asset {
    @Id
    private String id;

    @Column(nullable = false)
    private String broadcaster;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String url;
    private double x;
    private double y;
    private double width;
    private double height;
    private double rotation;
    private Double speed;
    private Boolean muted;
    private String mediaType;
    private String originalMediaType;
    private Integer zIndex;
    private Boolean audioLoop;
    private Integer audioDelayMillis;
    private Double audioSpeed;
    private Double audioPitch;
    private Double audioVolume;
    private boolean hidden;
    private Instant createdAt;

    public Asset() {
    }

    public Asset(String broadcaster, String name, String url, double width, double height) {
        this.id = UUID.randomUUID().toString();
        this.broadcaster = normalize(broadcaster);
        this.name = name;
        this.url = url;
        this.width = width;
        this.height = height;
        this.x = 0;
        this.y = 0;
        this.rotation = 0;
        this.speed = 1.0;
        this.muted = false;
        this.zIndex = 1;
        this.hidden = false;
        this.createdAt = Instant.now();
    }

    @PrePersist
    @PreUpdate
    public void prepare() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        this.broadcaster = normalize(broadcaster);
        if (this.name == null || this.name.isBlank()) {
            this.name = this.id;
        }
        if (this.speed == null) {
            this.speed = 1.0;
        }
        if (this.muted == null) {
            this.muted = Boolean.FALSE;
        }
        if (this.zIndex == null || this.zIndex < 1) {
            this.zIndex = 1;
        }
        if (this.audioLoop == null) {
            this.audioLoop = Boolean.FALSE;
        }
        if (this.audioDelayMillis == null) {
            this.audioDelayMillis = 0;
        }
        if (this.audioSpeed == null) {
            this.audioSpeed = 1.0;
        }
        if (this.audioPitch == null) {
            this.audioPitch = 1.0;
        }
        if (this.audioVolume == null) {
            this.audioVolume = 1.0;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
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

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public boolean isMuted() {
        return muted != null && muted;
    }

    public void setMuted(boolean muted) {
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

    public boolean isVideo() {
        return mediaType != null && mediaType.toLowerCase(Locale.ROOT).startsWith("video/");
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getZIndex() {
        return zIndex == null ? 1 : Math.max(1, zIndex);
    }

    public void setZIndex(Integer zIndex) {
        this.zIndex = zIndex == null ? null : Math.max(1, zIndex);
    }

    public boolean isAudioLoop() {
        return audioLoop != null && audioLoop;
    }

    public void setAudioLoop(Boolean audioLoop) {
        this.audioLoop = audioLoop;
    }

    public Integer getAudioDelayMillis() {
        return audioDelayMillis == null ? 0 : Math.max(0, audioDelayMillis);
    }

    public void setAudioDelayMillis(Integer audioDelayMillis) {
        this.audioDelayMillis = audioDelayMillis;
    }

    public double getAudioSpeed() {
        return audioSpeed == null ? 1.0 : Math.max(0.1, audioSpeed);
    }

    public void setAudioSpeed(Double audioSpeed) {
        this.audioSpeed = audioSpeed;
    }

    public double getAudioPitch() {
        return audioPitch == null ? 1.0 : Math.max(0.5, audioPitch);
    }

    public void setAudioPitch(Double audioPitch) {
        this.audioPitch = audioPitch;
    }

    public double getAudioVolume() {
        return audioVolume == null ? 1.0 : Math.max(0.0, Math.min(1.0, audioVolume));
    }

    public void setAudioVolume(Double audioVolume) {
        this.audioVolume = audioVolume;
    }

    private static String normalize(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
