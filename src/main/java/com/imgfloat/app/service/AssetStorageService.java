package com.imgfloat.app.service;

import com.imgfloat.app.service.media.AssetContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Optional;

@Service
public class AssetStorageService {
    private static final Logger logger = LoggerFactory.getLogger(AssetStorageService.class);
    private final Path assetRoot;
    private final Path previewRoot;

    public AssetStorageService(@Value("${IMGFLOAT_ASSETS_PATH:assets}") String assetRoot,
                               @Value("${IMGFLOAT_PREVIEWS_PATH:previews}") String previewRoot) {
        this.assetRoot = Paths.get(assetRoot);
        this.previewRoot = Paths.get(previewRoot);
    }

    public String storeAsset(String broadcaster, String assetId, byte[] assetBytes, String mediaType) throws IOException {
        if (assetBytes == null || assetBytes.length == 0) {
            throw new IOException("Asset content is empty");
        }
        Path directory = assetRoot.resolve(normalize(broadcaster));
        Files.createDirectories(directory);
        String extension = extensionForMediaType(mediaType);
        Path assetFile = directory.resolve(assetId + extension);
        Files.write(assetFile, assetBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        return assetFile.toString();
    }

    public String storePreview(String broadcaster, String assetId, byte[] previewBytes) throws IOException {
        if (previewBytes == null || previewBytes.length == 0) {
            return null;
        }
        Path directory = previewRoot.resolve(normalize(broadcaster));
        Files.createDirectories(directory);
        Path previewFile = directory.resolve(assetId + ".png");
        Files.write(previewFile, previewBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        return previewFile.toString();
    }

    public Optional<AssetContent> loadPreview(String previewPath) {
        if (previewPath == null || previewPath.isBlank()) {
            return Optional.empty();
        }
        try {
            Path path = Paths.get(previewPath);
            if (!Files.exists(path)) {
                return Optional.empty();
            }
            try {
                return Optional.of(new AssetContent(Files.readAllBytes(path), "image/png"));
            } catch (IOException e) {
                logger.warn("Unable to read preview from {}", previewPath, e);
                return Optional.empty();
            }
        } catch (InvalidPathException e) {
            logger.debug("Preview path {} is not a file path; skipping", previewPath);
            return Optional.empty();
        }
    }

    public Optional<AssetContent> loadAssetFile(String assetPath, String mediaType) {
        if (assetPath == null || assetPath.isBlank()) {
            return Optional.empty();
        }
        try {
            Path path = Paths.get(assetPath);
            if (!Files.exists(path)) {
                return Optional.empty();
            }
            try {
                String resolvedMediaType = mediaType;
                if (resolvedMediaType == null || resolvedMediaType.isBlank()) {
                    resolvedMediaType = Files.probeContentType(path);
                }
                if (resolvedMediaType == null || resolvedMediaType.isBlank()) {
                    resolvedMediaType = "application/octet-stream";
                }
                return Optional.of(new AssetContent(Files.readAllBytes(path), resolvedMediaType));
            } catch (IOException e) {
                logger.warn("Unable to read asset from {}", assetPath, e);
                return Optional.empty();
            }
        } catch (InvalidPathException e) {
            logger.debug("Asset path {} is not a file path; skipping", assetPath);
            return Optional.empty();
        }
    }

    public void deleteAssetFile(String assetPath) {
        if (assetPath == null || assetPath.isBlank()) {
            return;
        }
        try {
            Path path = Paths.get(assetPath);
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                logger.warn("Unable to delete asset file {}", assetPath, e);
            }
        } catch (InvalidPathException e) {
            logger.debug("Asset value {} is not a file path; nothing to delete", assetPath);
        }
    }

    public void deletePreviewFile(String previewPath) {
        if (previewPath == null || previewPath.isBlank()) {
            return;
        }
        try {
            Path path = Paths.get(previewPath);
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                logger.warn("Unable to delete preview file {}", previewPath, e);
            }
        } catch (InvalidPathException e) {
            logger.debug("Preview value {} is not a file path; nothing to delete", previewPath);
        }
    }

    private String extensionForMediaType(String mediaType) {
        if (mediaType == null || mediaType.isBlank()) {
            return ".bin";
        }
        return switch (mediaType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> ".png";
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/gif" -> ".gif";
            case "video/mp4" -> ".mp4";
            case "video/webm" -> ".webm";
            case "video/quicktime" -> ".mov";
            case "audio/mpeg" -> ".mp3";
            case "audio/wav" -> ".wav";
            case "audio/ogg" -> ".ogg";
            default -> {
                int slash = mediaType.indexOf('/');
                if (slash > -1 && slash < mediaType.length() - 1) {
                    yield "." + mediaType.substring(slash + 1).replaceAll("[^a-z0-9.+-]", "");
                }
                yield ".bin";
            }
        };
    }

    private String normalize(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
