package com.imgfloat.app.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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
@Table(name = "channels")
public class Channel {
    @Id
    private String broadcaster;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "channel_admins", joinColumns = @JoinColumn(name = "channel_id"))
    @Column(name = "admin_username")
    private Set<String> admins = new HashSet<>();

    public Channel() {
    }

    public Channel(String broadcaster) {
        this.broadcaster = normalize(broadcaster);
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

    @PrePersist
    @PreUpdate
    public void normalizeFields() {
        this.broadcaster = normalize(broadcaster);
        this.admins = admins.stream()
                .map(Channel::normalize)
                .collect(Collectors.toSet());
    }

    private static String normalize(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
