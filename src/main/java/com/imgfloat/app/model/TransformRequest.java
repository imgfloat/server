package com.imgfloat.app.model;

public class TransformRequest {
    private double x;
    private double y;
    private double width;
    private double height;
    private double rotation;
    private Double speed;
    private Boolean muted;
    private Integer zIndex;
    private Boolean audioLoop;
    private Integer audioDelayMillis;
    private Double audioSpeed;
    private Double audioPitch;
    private Double audioVolume;

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

    public Integer getZIndex() {
        return zIndex;
    }

    public void setZIndex(Integer zIndex) {
        this.zIndex = zIndex;
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
