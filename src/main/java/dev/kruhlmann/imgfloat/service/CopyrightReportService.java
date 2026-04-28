package dev.kruhlmann.imgfloat.service;

import dev.kruhlmann.imgfloat.model.api.request.CopyrightReportRequest;
import dev.kruhlmann.imgfloat.model.api.request.CopyrightReportReviewRequest;
import dev.kruhlmann.imgfloat.model.db.imgfloat.Channel;
import dev.kruhlmann.imgfloat.model.db.imgfloat.CopyrightReport;
import dev.kruhlmann.imgfloat.model.db.imgfloat.CopyrightReportStatus;
import dev.kruhlmann.imgfloat.repository.AssetRepository;
import dev.kruhlmann.imgfloat.repository.ChannelRepository;
import dev.kruhlmann.imgfloat.repository.CopyrightReportRepository;
import dev.kruhlmann.imgfloat.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class CopyrightReportService {

    private static final Logger LOG = LoggerFactory.getLogger(CopyrightReportService.class);

    private final CopyrightReportRepository copyrightReportRepository;
    private final AssetRepository assetRepository;
    private final ChannelRepository channelRepository;
    private final ChannelDirectoryService channelDirectoryService;
    private final AuditLogService auditLogService;
    private final SimpMessagingTemplate messagingTemplate;

    public CopyrightReportService(
        CopyrightReportRepository copyrightReportRepository,
        AssetRepository assetRepository,
        ChannelRepository channelRepository,
        ChannelDirectoryService channelDirectoryService,
        AuditLogService auditLogService,
        SimpMessagingTemplate messagingTemplate
    ) {
        this.copyrightReportRepository = copyrightReportRepository;
        this.assetRepository = assetRepository;
        this.channelRepository = channelRepository;
        this.channelDirectoryService = channelDirectoryService;
        this.auditLogService = auditLogService;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public CopyrightReport submitReport(String assetId, CopyrightReportRequest request) {
        var asset = assetRepository.findById(assetId).orElseThrow(() ->
            new ResponseStatusException(NOT_FOUND, "Asset not found")
        );
        if (request.goodFaithDeclaration() == null || !request.goodFaithDeclaration()) {
            throw new ResponseStatusException(BAD_REQUEST, "Good faith declaration must be confirmed");
        }
        CopyrightReport report = new CopyrightReport();
        report.setAssetId(assetId);
        report.setBroadcaster(asset.getBroadcaster());
        report.setClaimantName(request.claimantName().trim());
        report.setClaimantEmail(request.claimantEmail().trim());
        report.setOriginalWorkDescription(request.originalWorkDescription().trim());
        report.setInfringingDescription(request.infringingDescription().trim());
        report.setGoodFaithDeclaration(true);
        report.setStatus(CopyrightReportStatus.PENDING);
        CopyrightReport saved = copyrightReportRepository.save(report);
        auditLogService.recordEntry(
            asset.getBroadcaster(),
            "system",
            "COPYRIGHT_REPORT_SUBMITTED",
            "Copyright report submitted for asset " + assetId + " by claimant " + LogSanitizer.sanitize(request.claimantEmail())
        );
        LOG.info(
            "Copyright report {} submitted for asset {} (broadcaster: {})",
            saved.getId(),
            LogSanitizer.sanitize(assetId),
            LogSanitizer.sanitize(asset.getBroadcaster())
        );
        return saved;
    }

    public Page<CopyrightReport> listReports(
        CopyrightReportStatus status,
        String broadcaster,
        int page,
        int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        PageRequest pageRequest = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        String normalizedBroadcaster = (broadcaster != null && !broadcaster.isBlank())
            ? broadcaster.toLowerCase(java.util.Locale.ROOT)
            : null;
        return copyrightReportRepository.searchReports(status, normalizedBroadcaster, pageRequest);
    }

    public CopyrightReport getReport(String reportId) {
        return copyrightReportRepository.findById(reportId).orElseThrow(() ->
            new ResponseStatusException(NOT_FOUND, "Report not found")
        );
    }

    /** Returns all NOTIFIED reports for a broadcaster (their pending notices). */
    public List<CopyrightReport> listNotices(String broadcaster) {
        String normalized = broadcaster.toLowerCase(Locale.ROOT);
        return copyrightReportRepository.findByBroadcasterAndStatusOrderByCreatedAtDesc(
            normalized, CopyrightReportStatus.NOTIFIED
        );
    }

    /** Broadcaster acknowledges a notice — transitions it to RESOLVED. */
    @Transactional
    public void dismissNotice(String reportId, String broadcaster) {
        String normalized = broadcaster.toLowerCase(Locale.ROOT);
        CopyrightReport report = copyrightReportRepository.findById(reportId).orElseThrow(() ->
            new ResponseStatusException(NOT_FOUND, "Notice not found")
        );
        if (!report.getBroadcaster().equals(normalized)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Not your notice");
        }
        if (report.getStatus() != CopyrightReportStatus.NOTIFIED) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Notice is not in NOTIFIED state");
        }
        report.setStatus(CopyrightReportStatus.RESOLVED);
        copyrightReportRepository.save(report);
        auditLogService.recordEntry(
            normalized,
            normalized,
            "COPYRIGHT_NOTICE_DISMISSED",
            "Broadcaster acknowledged and dismissed copyright notice for report " + reportId
        );
    }

    @Transactional
    public CopyrightReport reviewReport(String reportId, CopyrightReportReviewRequest request, String reviewerUsername) {
        CopyrightReport report = copyrightReportRepository.findById(reportId).orElseThrow(() ->
            new ResponseStatusException(NOT_FOUND, "Report not found")
        );
        String broadcaster = report.getBroadcaster();

        switch (request.action()) {
            case DISMISS -> {
                report.setStatus(CopyrightReportStatus.DISMISSED);
                report.setResolutionNotes(request.resolutionNotes());
                report.setResolvedBy(reviewerUsername);
                auditLogService.recordEntry(
                    broadcaster,
                    reviewerUsername,
                    "COPYRIGHT_REPORT_DISMISSED",
                    "Report " + reportId + " dismissed"
                );
                LOG.info("Copyright report {} dismissed by {}", reportId, LogSanitizer.sanitize(reviewerUsername));
            }
            case REMOVE_ASSET -> {
                boolean deleted = channelDirectoryService.deleteAsset(report.getAssetId(), reviewerUsername);
                if (!deleted) {
                    throw new ResponseStatusException(NOT_FOUND, "Asset not found or already deleted");
                }
                report.setStatus(CopyrightReportStatus.RESOLVED);
                report.setResolutionNotes(request.resolutionNotes());
                report.setResolvedBy(reviewerUsername);
                auditLogService.recordEntry(
                    broadcaster,
                    reviewerUsername,
                    "ASSET_REMOVED_COPYRIGHT",
                    "Asset " + report.getAssetId() + " removed following copyright report " + reportId
                );
                LOG.info("Asset {} removed for copyright by {} (report: {})",
                    LogSanitizer.sanitize(report.getAssetId()),
                    LogSanitizer.sanitize(reviewerUsername),
                    reportId
                );
            }
            case NOTIFY_BROADCASTER -> {
                report.setStatus(CopyrightReportStatus.NOTIFIED);
                report.setResolutionNotes(request.resolutionNotes());
                report.setResolvedBy(reviewerUsername);
                messagingTemplate.convertAndSend(
                    "/topic/channel/" + broadcaster,
                    Map.of(
                        "type", "COPYRIGHT_WARNING",
                        "reportId", reportId,
                        "assetId", report.getAssetId(),
                        "message", "A copyright infringement report has been filed against one of your assets. Please review."
                    )
                );
                auditLogService.recordEntry(
                    broadcaster,
                    reviewerUsername,
                    "BROADCASTER_NOTIFIED_COPYRIGHT",
                    "Broadcaster notified of copyright report " + reportId + " for asset " + report.getAssetId()
                );
                LOG.info("Broadcaster {} notified of copyright report {} by {}",
                    LogSanitizer.sanitize(broadcaster),
                    reportId,
                    LogSanitizer.sanitize(reviewerUsername)
                );
            }
            case BAN_BROADCASTER -> {
                Channel channel = channelRepository.findById(broadcaster).orElseThrow(() ->
                    new ResponseStatusException(NOT_FOUND, "Channel not found")
                );
                channel.setBanned(true);
                channelRepository.save(channel);
                report.setStatus(CopyrightReportStatus.RESOLVED);
                report.setResolutionNotes(request.resolutionNotes());
                report.setResolvedBy(reviewerUsername);
                auditLogService.recordEntry(
                    broadcaster,
                    reviewerUsername,
                    "BROADCASTER_BANNED_COPYRIGHT",
                    "Broadcaster banned following copyright report " + reportId
                );
                LOG.info("Broadcaster {} banned for copyright by {} (report: {})",
                    LogSanitizer.sanitize(broadcaster),
                    LogSanitizer.sanitize(reviewerUsername),
                    reportId
                );
            }
        }

        return copyrightReportRepository.save(report);
    }
}
