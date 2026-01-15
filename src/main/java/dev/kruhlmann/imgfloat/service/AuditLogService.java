package dev.kruhlmann.imgfloat.service;

import dev.kruhlmann.imgfloat.model.AuditLogEntry;
import dev.kruhlmann.imgfloat.model.AuditLogEntryView;
import dev.kruhlmann.imgfloat.repository.AuditLogRepository;
import dev.kruhlmann.imgfloat.util.LogSanitizer;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    private static final Logger LOG = LoggerFactory.getLogger(AuditLogService.class);
    private static final String DEFAULT_ACTOR = "system";

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void recordEntry(String broadcaster, String actor, String action, String details) {
        String normalizedBroadcaster = normalize(broadcaster);
        if (normalizedBroadcaster == null || normalizedBroadcaster.isBlank()) {
            return;
        }
        String normalizedActor = normalize(actor);
        if (normalizedActor == null || normalizedActor.isBlank()) {
            normalizedActor = DEFAULT_ACTOR;
        }
        try {
            AuditLogEntry entry = new AuditLogEntry(normalizedBroadcaster, normalizedActor, action, details);
            auditLogRepository.save(entry);
            LOG.info(
                "Audit log entry created for broadcaster {} by {}: {}",
                LogSanitizer.sanitize(normalizedBroadcaster),
                LogSanitizer.sanitize(normalizedActor),
                action
            );
        } catch (DataAccessException ex) {
            LOG.warn(
                "Unable to save audit log entry for broadcaster {} by {}",
                LogSanitizer.sanitize(normalizedBroadcaster),
                LogSanitizer.sanitize(normalizedActor),
                ex
            );
        }
    }

    public List<AuditLogEntryView> listEntries(String broadcaster) {
        String normalizedBroadcaster = normalize(broadcaster);
        if (normalizedBroadcaster == null || normalizedBroadcaster.isBlank()) {
            return List.of();
        }
        return auditLogRepository
            .findTop200ByBroadcasterOrderByCreatedAtDesc(normalizedBroadcaster)
            .stream()
            .map(AuditLogEntryView::fromEntry)
            .toList();
    }

    public void deleteEntriesForBroadcaster(String broadcaster) {
        String normalizedBroadcaster = normalize(broadcaster);
        if (normalizedBroadcaster == null || normalizedBroadcaster.isBlank()) {
            return;
        }
        auditLogRepository.deleteByBroadcaster(normalizedBroadcaster);
    }

    private String normalize(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
