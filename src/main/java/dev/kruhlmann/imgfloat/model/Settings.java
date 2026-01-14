package dev.kruhlmann.imgfloat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "settings")
public class Settings {

    @Id
    @Column(nullable = false)
    private int id = 1;

    @Column(nullable = false)
    private double minAssetPlaybackSpeedFraction;

    @Column(nullable = false)
    private double maxAssetPlaybackSpeedFraction;

    @Column(nullable = false)
    private double minAssetAudioPitchFraction;

    @Column(nullable = false)
    private double maxAssetAudioPitchFraction;

    @Column(nullable = false)
    private double minAssetVolumeFraction;

    @Column(nullable = false)
    private double maxAssetVolumeFraction;

    @Column(nullable = false)
    private int maxCanvasSideLengthPixels;

    @Column(nullable = false)
    private int canvasFramesPerSecond;

    @Column(nullable = false)
    private int emoteSyncIntervalMinutes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Settings() {}

    public static Settings defaults() {
        Settings s = new Settings();
        s.setMinAssetPlaybackSpeedFraction(0.1);
        s.setMaxAssetPlaybackSpeedFraction(4.0);
        s.setMinAssetAudioPitchFraction(0.1);
        s.setMaxAssetAudioPitchFraction(4.0);
        s.setMinAssetVolumeFraction(0.01);
        s.setMaxAssetVolumeFraction(5.0);
        s.setMaxCanvasSideLengthPixels(7680);
        s.setCanvasFramesPerSecond(60);
        s.setEmoteSyncIntervalMinutes(60);
        return s;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public double getMinAssetPlaybackSpeedFraction() {
        return minAssetPlaybackSpeedFraction;
    }

    public void setMinAssetPlaybackSpeedFraction(double value) {
        this.minAssetPlaybackSpeedFraction = value;
    }

    public double getMaxAssetPlaybackSpeedFraction() {
        return maxAssetPlaybackSpeedFraction;
    }

    public void setMaxAssetPlaybackSpeedFraction(double value) {
        this.maxAssetPlaybackSpeedFraction = value;
    }

    public double getMinAssetAudioPitchFraction() {
        return minAssetAudioPitchFraction;
    }

    public void setMinAssetAudioPitchFraction(double value) {
        this.minAssetAudioPitchFraction = value;
    }

    public double getMaxAssetAudioPitchFraction() {
        return maxAssetAudioPitchFraction;
    }

    public void setMaxAssetAudioPitchFraction(double value) {
        this.maxAssetAudioPitchFraction = value;
    }

    public double getMinAssetVolumeFraction() {
        return minAssetVolumeFraction;
    }

    public void setMinAssetVolumeFraction(double value) {
        this.minAssetVolumeFraction = value;
    }

    public double getMaxAssetVolumeFraction() {
        return maxAssetVolumeFraction;
    }

    public void setMaxAssetVolumeFraction(double value) {
        this.maxAssetVolumeFraction = value;
    }

    public int getCanvasFramesPerSecond() {
        return canvasFramesPerSecond;
    }

    public int getMaxCanvasSideLengthPixels() {
        return maxCanvasSideLengthPixels;
    }

    public void setMaxCanvasSideLengthPixels(int maxCanvasSideLengthPixels) {
        this.maxCanvasSideLengthPixels = maxCanvasSideLengthPixels;
    }

    public void setCanvasFramesPerSecond(int canvasFramesPerSecond) {
        this.canvasFramesPerSecond = canvasFramesPerSecond;
    }

    public int getEmoteSyncIntervalMinutes() {
        return emoteSyncIntervalMinutes;
    }

    public void setEmoteSyncIntervalMinutes(int emoteSyncIntervalMinutes) {
        this.emoteSyncIntervalMinutes = emoteSyncIntervalMinutes;
    }

    @PrePersist
    public void initializeTimestamps() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    public void updateTimestamp() {
        updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
