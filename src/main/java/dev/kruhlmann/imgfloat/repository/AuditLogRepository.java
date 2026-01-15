package dev.kruhlmann.imgfloat.repository;

import dev.kruhlmann.imgfloat.model.AuditLogEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntry, String> {
    List<AuditLogEntry> findTop200ByBroadcasterOrderByCreatedAtDesc(String broadcaster);

    void deleteByBroadcaster(String broadcaster);
}
