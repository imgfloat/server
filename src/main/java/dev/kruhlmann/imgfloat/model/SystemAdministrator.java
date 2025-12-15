package dev.kruhlmann.imgfloat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(
    name = "system_administrators",
    uniqueConstraints = @UniqueConstraint(columnNames = "twitch_username")
)
public class SystemAdministrator {
    @Id
    private String id;
    @Column(name = "twitch_username", nullable = false)
    private String twitchUsername;

    public SystemAdministrator() {
    }

    public SystemAdministrator(String twitchUsername) {
        this.twitchUsername = twitchUsername;
    }

    @PrePersist
    @PreUpdate
    public void prepare() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
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
}
