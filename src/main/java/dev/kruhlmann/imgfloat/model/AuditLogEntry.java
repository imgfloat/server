package dev.kruhlmann.imgfloat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "channel_audit_log")
public class AuditLogEntry {

    @Id
    private String id;

    @Column(nullable = false)
    private String broadcaster;

    @Column(nullable = false)
    private String action;

    @Column
    private String actor;

    @Column
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public AuditLogEntry() {}

    public AuditLogEntry(String broadcaster, String actor, String action, String details) {
        this.id = UUID.randomUUID().toString();
        this.broadcaster = normalize(broadcaster);
        this.actor = normalize(actor);
        this.action = action == null || action.isBlank() ? "UNKNOWN" : action;
        this.details = details;
        this.createdAt = Instant.now();
    }

    @PrePersist
    public void prepare() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        broadcaster = normalize(broadcaster);
        actor = normalize(actor);
        if (action == null || action.isBlank()) {
            action = "UNKNOWN";
        }
    }

    public String getId() {
        return id;
    }

    public String getBroadcaster() {
        return broadcaster;
    }

    public String getAction() {
        return action;
    }

    public String getActor() {
        return actor;
    }

    public String getDetails() {
        return details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private static String normalize(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
