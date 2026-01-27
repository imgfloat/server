package dev.kruhlmann.imgfloat.service;

import dev.kruhlmann.imgfloat.model.db.imgfloat.Asset;
import dev.kruhlmann.imgfloat.model.db.imgfloat.ScriptAssetFile;
import dev.kruhlmann.imgfloat.repository.AssetRepository;
import dev.kruhlmann.imgfloat.repository.ChannelRepository;
import dev.kruhlmann.imgfloat.repository.MarketplaceScriptHeartRepository;
import dev.kruhlmann.imgfloat.repository.ScriptAssetFileRepository;
import dev.kruhlmann.imgfloat.repository.SystemAdministratorRepository;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private static final Logger LOG = LoggerFactory.getLogger(AccountService.class);

    private final ChannelDirectoryService channelDirectoryService;
    private final AssetRepository assetRepository;
    private final ChannelRepository channelRepository;
    private final ScriptAssetFileRepository scriptAssetFileRepository;
    private final MarketplaceScriptHeartRepository marketplaceScriptHeartRepository;
    private final SystemAdministratorRepository systemAdministratorRepository;
    private final AssetStorageService assetStorageService;
    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;

    public AccountService(
        ChannelDirectoryService channelDirectoryService,
        AssetRepository assetRepository,
        ChannelRepository channelRepository,
        ScriptAssetFileRepository scriptAssetFileRepository,
        MarketplaceScriptHeartRepository marketplaceScriptHeartRepository,
        SystemAdministratorRepository systemAdministratorRepository,
        AssetStorageService assetStorageService,
        JdbcTemplate jdbcTemplate,
        AuditLogService auditLogService
    ) {
        this.channelDirectoryService = channelDirectoryService;
        this.assetRepository = assetRepository;
        this.channelRepository = channelRepository;
        this.scriptAssetFileRepository = scriptAssetFileRepository;
        this.marketplaceScriptHeartRepository = marketplaceScriptHeartRepository;
        this.systemAdministratorRepository = systemAdministratorRepository;
        this.assetStorageService = assetStorageService;
        this.jdbcTemplate = jdbcTemplate;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public void deleteAccount(String username) {
        String normalized = normalize(username);
        if (normalized == null || normalized.isBlank()) {
            return;
        }

        List<String> assetIds = assetRepository
            .findByBroadcaster(normalized)
            .stream()
            .map(Asset::getId)
            .toList();
        assetIds.forEach((assetId) -> channelDirectoryService.deleteAsset(assetId, normalized));

        List<ScriptAssetFile> scriptFiles = scriptAssetFileRepository.findByBroadcaster(normalized);
        scriptFiles.forEach(this::deleteScriptAssetFile);

        marketplaceScriptHeartRepository.deleteByUsername(normalized);
        systemAdministratorRepository.deleteByTwitchUsername(normalized);
        channelRepository.deleteById(normalized);
        auditLogService.deleteEntriesForBroadcaster(normalized);

        deleteSessionsForUser(normalized);
        LOG.info("Account data deleted for {}", normalized);
    }

    private void deleteScriptAssetFile(ScriptAssetFile file) {
        if (file == null) {
            return;
        }
        assetStorageService.deleteAsset(file.getBroadcaster(), file.getId(), file.getMediaType(), false);
        scriptAssetFileRepository.delete(file);
    }

    private void deleteSessionsForUser(String username) {
        jdbcTemplate.update(
            "DELETE FROM SPRING_SESSION_ATTRIBUTES WHERE SESSION_PRIMARY_ID IN (" +
            "SELECT PRIMARY_ID FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?)",
            username
        );
        jdbcTemplate.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", username);
    }

    private String normalize(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
