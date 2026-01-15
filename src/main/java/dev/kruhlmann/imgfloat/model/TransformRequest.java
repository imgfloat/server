package dev.kruhlmann.imgfloat.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public class TransformRequest {

    private Double x;
    private Double y;

    @Positive(message = "Width must be greater than 0")
    private Double width;

    @Positive(message = "Height must be greater than 0")
    private Double height;

    private Double rotation;

    @DecimalMin(value = "0.0", message = "Playback speed cannot be negative")
    @DecimalMax(value = "4.0", message = "Playback speed cannot exceed 4.0")
    private Double speed;

    private Boolean muted;

    @Positive(message = "Order must be at least 1")
    private Integer order;

    private Boolean audioLoop;

    @PositiveOrZero(message = "Audio delay must be zero or greater")
    private Integer audioDelayMillis;

    @DecimalMin(value = "0.1", message = "Audio speed must be at least 0.1x")
    @DecimalMax(value = "4.0", message = "Audio speed cannot exceed 4.0x")
    private Double audioSpeed;

    @DecimalMin(value = "0.5", message = "Audio pitch must be at least 0.5x")
    @DecimalMax(value = "2.0", message = "Audio pitch cannot exceed 2.0x")
    private Double audioPitch;

    @DecimalMin(value = "0.0", message = "Audio volume cannot be negative")
    @DecimalMax(value = "1.0", message = "Audio volume cannot exceed 1.0")
    private Double audioVolume;

    public Double getX() {
        return x;
    }

    public void setX(Double x) {
        this.x = x;
    }

    public Double getY() {
        return y;
    }

    public void setY(Double y) {
        this.y = y;
    }

    public Double getWidth() {
        return width;
    }

    public void setWidth(Double width) {
        this.width = width;
    }

    public Double getHeight() {
        return height;
    }

    public void setHeight(Double height) {
        this.height = height;
    }

    public Double getRotation() {
        return rotation;
    }

    public void setRotation(Double rotation) {
        this.rotation = rotation;
    }

    public Double getSpeed() {
        return speed;
    }

    public void setSpeed(Double speed) {
        this.speed = speed;
    }

    public Boolean getMuted() {
        return muted;
    }

    public void setMuted(Boolean muted) {
        this.muted = muted;
    }

    public Integer getOrder() {
        return order;
    }

    public void setOrder(Integer order) {
        this.order = order;
    }

    public Boolean getAudioLoop() {
        return audioLoop;
    }

    public void setAudioLoop(Boolean audioLoop) {
        this.audioLoop = audioLoop;
    }

    public Integer getAudioDelayMillis() {
        return audioDelayMillis;
    }

    public void setAudioDelayMillis(Integer audioDelayMillis) {
        this.audioDelayMillis = audioDelayMillis;
    }

    public Double getAudioSpeed() {
        return audioSpeed;
    }

    public void setAudioSpeed(Double audioSpeed) {
        this.audioSpeed = audioSpeed;
    }

    public Double getAudioPitch() {
        return audioPitch;
    }

    public void setAudioPitch(Double audioPitch) {
        this.audioPitch = audioPitch;
    }

    public Double getAudioVolume() {
        return audioVolume;
    }

    public void setAudioVolume(Double audioVolume) {
        this.audioVolume = audioVolume;
    }
}
