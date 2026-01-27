package dev.kruhlmann.imgfloat.model.db.imgfloat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "system_administrators", uniqueConstraints = @UniqueConstraint(columnNames = "twitch_username"))
public class SystemAdministrator {

    @Id
    private String id;

    @Column(name = "twitch_username", nullable = false)
    private String twitchUsername;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public SystemAdministrator() {}

    public SystemAdministrator(String twitchUsername) {
        this.twitchUsername = twitchUsername;
    }

    @PrePersist
    @PreUpdate
    public void prepare() {
        Instant now = Instant.now();
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
        twitchUsername = twitchUsername.toLowerCase(Locale.ROOT);
    }

    public String getId() {
        return id;
    }

    public String getTwitchUsername() {
        return twitchUsername;
    }

    public void setTwitchUsername(String twitchUsername) {
        this.twitchUsername = twitchUsername;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
