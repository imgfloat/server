package dev.kruhlmann.imgfloat.model.db.imgfloat;

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
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Entity
@Table(name = "channels")
public class Channel {

    @Id
    private String broadcaster;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "channel_admins", joinColumns = @JoinColumn(name = "channel_id"))
    @Column(name = "admin_username")
    private final Set<String> admins = new HashSet<>();

    private double canvasWidth = 1920;

    private double canvasHeight = 1080;

    @Column(name = "allow_channel_emotes_for_assets", nullable = false)
    private boolean allowChannelEmotesForAssets = true;

    @Column(name = "allow_7tv_emotes_for_assets", nullable = false)
    private boolean allowSevenTvEmotesForAssets = true;

    @Column(name = "allow_script_chat_access", nullable = false)
    private boolean allowScriptChatAccess = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Channel() {}

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

    public boolean isAllowChannelEmotesForAssets() {
        return allowChannelEmotesForAssets;
    }

    public void setAllowChannelEmotesForAssets(boolean allowChannelEmotesForAssets) {
        this.allowChannelEmotesForAssets = allowChannelEmotesForAssets;
    }

    public boolean isAllowSevenTvEmotesForAssets() {
        return allowSevenTvEmotesForAssets;
    }

    public void setAllowSevenTvEmotesForAssets(boolean allowSevenTvEmotesForAssets) {
        this.allowSevenTvEmotesForAssets = allowSevenTvEmotesForAssets;
    }

    public boolean isAllowScriptChatAccess() {
        return allowScriptChatAccess;
    }

    public void setAllowScriptChatAccess(boolean allowScriptChatAccess) {
        this.allowScriptChatAccess = allowScriptChatAccess;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @PrePersist
    private void ensureCreatedAt() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    private void touchUpdatedAt() {
        updatedAt = Instant.now();
    }

    private static String normalize(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
