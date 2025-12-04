package com.imgfloat.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

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

    @Column(columnDefinition = "TEXT")
    private String url;
    private double x;
    private double y;
    private double width;
    private double height;
    private double rotation;
    private boolean hidden;
    private Instant createdAt;

    public Asset() {
    }

    public Asset(String broadcaster, String url, double width, double height) {
        this.id = UUID.randomUUID().toString();
        this.broadcaster = normalize(broadcaster);
        this.url = url;
        this.width = width;
        this.height = height;
        this.x = 0;
        this.y = 0;
        this.rotation = 0;
        this.hidden = false;
        this.createdAt = Instant.now();
    }

    @PrePersist
    public void prepare() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        this.broadcaster = normalize(broadcaster);
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

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    private static String normalize(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
