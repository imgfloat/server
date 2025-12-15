package dev.kruhlmann.imgfloat.service;

import dev.kruhlmann.imgfloat.model.Asset;
import dev.kruhlmann.imgfloat.repository.AssetRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class AssetCleanupService {

    private static final Logger logger =
            LoggerFactory.getLogger(AssetCleanupService.class);

    private final AssetRepository assetRepository;
    private final AssetStorageService assetStorageService;

    public AssetCleanupService(
            AssetRepository assetRepository,
            AssetStorageService assetStorageService
    ) {
        this.assetRepository = assetRepository;
        this.assetStorageService = assetStorageService;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void cleanup() {
        logger.info("Collecting referenced assets");

        Set<String> referencedIds = assetRepository.findAll()
                .stream()
                .map(Asset::getId)
                .collect(Collectors.toSet());

        assetStorageService.deleteOrphanedAssets(referencedIds);
    }
}
