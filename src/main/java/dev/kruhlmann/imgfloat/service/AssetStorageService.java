package dev.kruhlmann.imgfloat.service;

import dev.kruhlmann.imgfloat.service.media.AssetContent;
import java.io.IOException;
import java.nio.file.*;
import dev.kruhlmann.imgfloat.service.media.MediaTypeRegistry;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AssetStorageService {

    private static final Logger logger = LoggerFactory.getLogger(AssetStorageService.class);
    private static final String DEFAULT_PREVIEW_MEDIA_TYPE = "image/png";

    private final Path assetRoot;
    private final Path previewRoot;

    public AssetStorageService(
        @Value("${IMGFLOAT_ASSETS_PATH:#{null}}") String assetRoot,
        @Value("${IMGFLOAT_PREVIEWS_PATH:#{null}}") String previewRoot
    ) {
        String assetsBase = assetRoot != null
            ? assetRoot
            : Paths.get(System.getProperty("java.io.tmpdir"), "imgfloat-assets").toString();
        String previewsBase = previewRoot != null
            ? previewRoot
            : Paths.get(System.getProperty("java.io.tmpdir"), "imgfloat-previews").toString();

        this.assetRoot = Paths.get(assetsBase).normalize().toAbsolutePath();
        this.previewRoot = Paths.get(previewsBase).normalize().toAbsolutePath();
        try {
            Files.createDirectories(this.assetRoot);
            Files.createDirectories(this.previewRoot);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create asset storage directories", e);
        }
    }

    public void storeAsset(String broadcaster, String assetId, byte[] assetBytes, String mediaType) throws IOException {
        if (assetBytes == null || assetBytes.length == 0) {
            throw new IOException("Asset content is empty");
        }

        Path file = assetPath(broadcaster, assetId, mediaType);
        Files.createDirectories(file.getParent());

        Files.write(
            file,
            assetBytes,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        );
        logger.info("Wrote asset to {}", file);
    }

    public void storePreview(String broadcaster, String assetId, byte[] previewBytes) throws IOException {
        if (previewBytes == null || previewBytes.length == 0) return;

        Path file = previewPath(broadcaster, assetId);
        Files.createDirectories(file.getParent());

        Files.write(
            file,
            previewBytes,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        );
        logger.info("Wrote asset to {}", file);
    }

    public Optional<AssetContent> loadAssetFile(String broadcaster, String assetId, String mediaType) {
        try {
            Path file = assetPath(broadcaster, assetId, mediaType);

            if (!Files.exists(file)) return Optional.empty();

            byte[] bytes = Files.readAllBytes(file);
            return Optional.of(new AssetContent(bytes, mediaType));
        } catch (Exception e) {
            logger.warn("Failed to load asset {}", assetId, e);
            return Optional.empty();
        }
    }

    public Optional<AssetContent> loadPreview(String broadcaster, String assetId) {
        try {
            Path file = previewPath(broadcaster, assetId);
            if (!Files.exists(file)) return Optional.empty();

            byte[] bytes = Files.readAllBytes(file);
            return Optional.of(new AssetContent(bytes, DEFAULT_PREVIEW_MEDIA_TYPE));
        } catch (Exception e) {
            logger.warn("Failed to load preview {}", assetId, e);
            return Optional.empty();
        }
    }

    public Optional<AssetContent> loadAssetFileSafely(String broadcaster, String assetId, String mediaType) {
        if (mediaType == null) {
            return Optional.empty();
        }
        return loadAssetFile(broadcaster, assetId, mediaType);
    }

    public Optional<AssetContent> loadPreviewSafely(String broadcaster, String assetId, boolean hasPreview) {
        if (!hasPreview) {
            return Optional.empty();
        }
        return loadPreview(broadcaster, assetId);
    }

    public void deleteAsset(String broadcaster, String assetId, String mediaType, boolean hasPreview) {
        try {
            if (mediaType != null) {
                Files.deleteIfExists(assetPath(broadcaster, assetId, mediaType));
            }
            if (hasPreview) {
                Files.deleteIfExists(previewPath(broadcaster, assetId));
            }
        } catch (Exception e) {
            logger.warn("Failed to delete asset {}", assetId, e);
        }
    }

    public void deleteOrphanedAssets(Set<String> referencedAssetIds) {
        deleteOrphansUnder(assetRoot, referencedAssetIds);
        deleteOrphansUnder(previewRoot, referencedAssetIds);
    }

    private void deleteOrphansUnder(Path root, Set<String> referencedAssetIds) {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths
                .filter(Files::isRegularFile)
                .filter((p) -> isOrphan(p, referencedAssetIds))
                .forEach((p) -> {
                    try {
                        Files.delete(p);
                        logger.warn("Deleted orphan file {}", p);
                    } catch (IOException e) {
                        logger.error("Failed to delete {}", p, e);
                    }
                });
        } catch (IOException e) {
            logger.error("Failed to walk {}", root, e);
        }
    }

    private boolean isOrphan(Path file, Set<String> referencedAssetIds) {
        String name = file.getFileName().toString();
        int dot = name.indexOf('.');
        if (dot == -1) return true;
        String assetId = name.substring(0, dot);
        return !referencedAssetIds.contains(assetId);
    }

    private String sanitizeUserSegment(String value) {
        if (value == null) throw new IllegalArgumentException("Broadcaster is null");

        String safe = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        if (safe.isBlank()) throw new IllegalArgumentException("Invalid broadcaster: " + value);
        return safe;
    }

    private String resolveExtension(String mediaType) throws IOException {
        return MediaTypeRegistry
            .extensionForMediaType(mediaType)
            .orElseThrow(() -> new IOException("Unsupported media type: " + mediaType));
    }

    private Path assetPath(String broadcaster, String assetId, String mediaType) throws IOException {
        String safeUser = sanitizeUserSegment(broadcaster);
        String extension = resolveExtension(mediaType);
        return safeJoin(assetRoot, safeUser).resolve(assetId + extension);
    }

    private Path previewPath(String broadcaster, String assetId) throws IOException {
        String safeUser = sanitizeUserSegment(broadcaster);
        return safeJoin(previewRoot, safeUser).resolve(assetId + ".png");
    }

    /**
     * Safe path-join that prevents path traversal.
     * Accepts both "abc/123.png" (relative multi-level) and single components.
     */
    private Path safeJoin(Path root, String relative) throws IOException {
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) {
            throw new IOException("Path traversal attempt: " + relative);
        }
        return resolved;
    }
}
