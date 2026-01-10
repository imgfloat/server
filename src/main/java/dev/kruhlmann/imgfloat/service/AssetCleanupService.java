package dev.kruhlmann.imgfloat.service;

import dev.kruhlmann.imgfloat.model.Asset;
import dev.kruhlmann.imgfloat.model.ScriptAssetAttachment;
import dev.kruhlmann.imgfloat.model.ScriptAsset;
import dev.kruhlmann.imgfloat.repository.AssetRepository;
import dev.kruhlmann.imgfloat.repository.ScriptAssetAttachmentRepository;
import dev.kruhlmann.imgfloat.repository.ScriptAssetRepository;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(AssetCleanupService.class);

    private final AssetRepository assetRepository;
    private final AssetStorageService assetStorageService;
    private final ScriptAssetAttachmentRepository scriptAssetAttachmentRepository;
    private final ScriptAssetRepository scriptAssetRepository;

    public AssetCleanupService(
        AssetRepository assetRepository,
        AssetStorageService assetStorageService,
        ScriptAssetAttachmentRepository scriptAssetAttachmentRepository,
        ScriptAssetRepository scriptAssetRepository
    ) {
        this.assetRepository = assetRepository;
        this.assetStorageService = assetStorageService;
        this.scriptAssetAttachmentRepository = scriptAssetAttachmentRepository;
        this.scriptAssetRepository = scriptAssetRepository;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void cleanup() {
        logger.info("Collecting referenced assets");

        Set<String> referencedIds = assetRepository
            .findAll()
            .stream()
            .map(Asset::getId)
            .collect(Collectors.toSet());
        referencedIds.addAll(
            scriptAssetAttachmentRepository
                .findAll()
                .stream()
                .map((attachment) -> attachment.getFileId() != null ? attachment.getFileId() : attachment.getId())
                .collect(Collectors.toSet())
        );
        referencedIds.addAll(
            scriptAssetRepository
                .findAll()
                .stream()
                .map(ScriptAsset::getSourceFileId)
                .filter((id) -> id != null && !id.isBlank())
                .collect(Collectors.toSet())
        );
        referencedIds.addAll(
            scriptAssetRepository
                .findAll()
                .stream()
                .map(ScriptAsset::getLogoFileId)
                .filter((id) -> id != null && !id.isBlank())
                .collect(Collectors.toSet())
        );

        assetStorageService.deleteOrphanedAssets(referencedIds);
    }
}
