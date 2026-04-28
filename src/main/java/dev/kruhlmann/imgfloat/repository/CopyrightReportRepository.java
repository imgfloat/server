package dev.kruhlmann.imgfloat.repository;

import dev.kruhlmann.imgfloat.model.db.imgfloat.CopyrightReport;
import dev.kruhlmann.imgfloat.model.db.imgfloat.CopyrightReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CopyrightReportRepository extends JpaRepository<CopyrightReport, String> {

    @Query("""
        SELECT r FROM CopyrightReport r
        WHERE (:status IS NULL OR r.status = :status)
          AND (:broadcaster IS NULL OR r.broadcaster = :broadcaster)
        ORDER BY r.createdAt DESC
        """)
    Page<CopyrightReport> searchReports(
        @Param("status") CopyrightReportStatus status,
        @Param("broadcaster") String broadcaster,
        Pageable pageable
    );

    List<CopyrightReport> findByBroadcasterAndStatusOrderByCreatedAtDesc(String broadcaster, CopyrightReportStatus status);

    void deleteByAssetId(String assetId);
}
