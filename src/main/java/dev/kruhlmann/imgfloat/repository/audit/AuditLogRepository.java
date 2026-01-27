package dev.kruhlmann.imgfloat.repository.audit;

import dev.kruhlmann.imgfloat.model.db.audit.AuditLogEntry;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntry, String> {
    List<AuditLogEntry> findTop200ByBroadcasterOrderByCreatedAtDesc(String broadcaster);

    @Query(
        """
        SELECT entry
        FROM AuditLogEntry entry
        WHERE entry.broadcaster = :broadcaster
            AND (:actor IS NULL OR LOWER(entry.actor) = :actor)
            AND (:action IS NULL OR LOWER(entry.action) LIKE CONCAT('%', :action, '%'))
            AND (
                :search IS NULL
                OR LOWER(entry.actor) LIKE CONCAT('%', :search, '%')
                OR LOWER(entry.action) LIKE CONCAT('%', :search, '%')
                OR LOWER(entry.details) LIKE CONCAT('%', :search, '%')
            )
        """
    )
    Page<AuditLogEntry> searchEntries(
        @Param("broadcaster") String broadcaster,
        @Param("actor") String actor,
        @Param("action") String action,
        @Param("search") String search,
        Pageable pageable
    );

    void deleteByBroadcaster(String broadcaster);
}
