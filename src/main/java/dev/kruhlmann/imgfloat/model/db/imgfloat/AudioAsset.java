package dev.kruhlmann.imgfloat.model.db.imgfloat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "audio_assets")
public class AudioAsset {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    private String mediaType;
    private String originalMediaType;
    private Boolean audioLoop;
    private Integer audioDelayMillis;
    private Double audioSpeed;
    private Double audioPitch;
    private Double audioVolume;
    private boolean hidden;

    public AudioAsset() {}

    public AudioAsset(String assetId, String name) {
        this.id = assetId;
        this.name = name;
        this.audioLoop = Boolean.FALSE;
        this.audioDelayMillis = 0;
        this.audioSpeed = 1.0;
        this.audioPitch = 1.0;
        this.audioVolume = 1.0;
        this.hidden = true;
    }

    @PrePersist
    @PreUpdate
    public void prepare() {
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
        return audioPitch == null ? 1.0 : Math.max(0.1, audioPitch);
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

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }
}
