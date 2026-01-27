package dev.kruhlmann.imgfloat.model.api.response;

import java.time.Instant;

import dev.kruhlmann.imgfloat.model.db.audit.AuditLogEntry;

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
