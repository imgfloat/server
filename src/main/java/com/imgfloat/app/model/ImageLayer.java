package com.imgfloat.app.model;

import java.time.Instant;
import java.util.UUID;

public class ImageLayer {
    private final String id;
    private String url;
    private double x;
    private double y;
    private double width;
    private double height;
    private double rotation;
    private boolean hidden;
    private final Instant createdAt;

    public ImageLayer(String url, double width, double height) {
        this.id = UUID.randomUUID().toString();
        this.url = url;
        this.width = width;
        this.height = height;
        this.x = 0;
        this.y = 0;
        this.rotation = 0;
        this.hidden = true;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
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

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
