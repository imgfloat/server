package com.imgfloat.app.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "channels", indexes = {
        @Index(name = "idx_channels_broadcaster", columnList = "broadcaster")
})
public class Channel {
    @Id
    private String broadcaster;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "channel_admins",
            joinColumns = @JoinColumn(name = "channel_id"),
            indexes = @Index(name = "idx_channel_admins_username", columnList = "admin_username"))
    @Column(name = "admin_username")
    private Set<String> admins = new HashSet<>();

    private double canvasWidth = 1920;

    private double canvasHeight = 1080;

    public Channel() {
    }

    public Channel(String broadcaster) {
        this.broadcaster = normalize(broadcaster);
        this.canvasWidth = 1920;
        this.canvasHeight = 1080;
    }

    public String getBroadcaster() {
        return broadcaster;
    }

    public Set<String> getAdmins() {
        return Collections.unmodifiableSet(admins);
    }

    public boolean addAdmin(String username) {
        return admins.add(normalize(username));
    }

    public boolean removeAdmin(String username) {
        return admins.remove(normalize(username));
    }

    public double getCanvasWidth() {
        return canvasWidth;
    }

    public void setCanvasWidth(double canvasWidth) {
        this.canvasWidth = canvasWidth;
    }

    public double getCanvasHeight() {
        return canvasHeight;
    }

    public void setCanvasHeight(double canvasHeight) {
        this.canvasHeight = canvasHeight;
    }

    @PrePersist
    @PreUpdate
    public void normalizeFields() {
        this.broadcaster = normalize(broadcaster);
        this.admins = admins.stream()
                .map(Channel::normalize)
                .collect(Collectors.toSet());
        if (canvasWidth <= 0) {
            canvasWidth = 1920;
        }
        if (canvasHeight <= 0) {
            canvasHeight = 1080;
        }
    }

    private static String normalize(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
