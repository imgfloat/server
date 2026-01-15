package dev.kruhlmann.imgfloat.model;

import java.time.Instant;

public record AuditLogEntryView(String id, String actor, String action, String details, Instant createdAt) {
    public static AuditLogEntryView fromEntry(AuditLogEntry entry) {
        if (entry == null) {
            return null;
        }
        return new AuditLogEntryView(
            entry.getId(),
            entry.getActor(),
            entry.getAction(),
            entry.getDetails(),
            entry.getCreatedAt()
        );
    }
}
