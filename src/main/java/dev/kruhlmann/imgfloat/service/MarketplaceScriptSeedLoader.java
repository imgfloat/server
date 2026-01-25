package dev.kruhlmann.imgfloat.service;

import dev.kruhlmann.imgfloat.model.ScriptMarketplaceEntry;
import dev.kruhlmann.imgfloat.service.media.AssetContent;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MarketplaceScriptSeedLoader {

    private static final Logger logger = LoggerFactory.getLogger(MarketplaceScriptSeedLoader.class);
    private static final String METADATA_FILENAME = "metadata.json";
    private static final String SOURCE_FILENAME = "source.js";
    private static final String LOGO_FILENAME = "logo.png";
    private static final String ATTACHMENTS_DIR = "attachments";
    private static final String DEFAULT_SOURCE_MEDIA_TYPE = "application/javascript";
    private static final String DEFAULT_LOGO_MEDIA_TYPE = "image/png";
    private static final java.util.Map<String, String> ATTACHMENT_MEDIA_TYPES = java.util.Map.ofEntries(
        java.util.Map.entry("png", "image/png"),
        java.util.Map.entry("jpg", "image/jpeg"),
        java.util.Map.entry("jpeg", "image/jpeg"),
        java.util.Map.entry("gif", "image/gif"),
        java.util.Map.entry("webp", "image/webp"),
        java.util.Map.entry("svg", "image/svg+xml"),
        java.util.Map.entry("mp4", "video/mp4"),
        java.util.Map.entry("webm", "video/webm"),
        java.util.Map.entry("mov", "video/quicktime"),
        java.util.Map.entry("mp3", "audio/mpeg"),
        java.util.Map.entry("wav", "audio/wav"),
        java.util.Map.entry("ogg", "audio/ogg"),
        java.util.Map.entry("ttf", "font/ttf"),
        java.util.Map.entry("otf", "font/otf"),
        java.util.Map.entry("woff", "font/woff"),
        java.util.Map.entry("woff2", "font/woff2"),
        java.util.Map.entry("glb", "model/gltf-binary"),
        java.util.Map.entry("gltf", "model/gltf+json"),
        java.util.Map.entry("obj", "model/obj")
    );

    private final List<SeedScript> scripts;

    public MarketplaceScriptSeedLoader(@Value("${IMGFLOAT_MARKETPLACE_SCRIPTS_PATH:#{null}}") String rootPath) {
        this.scripts = loadScripts(resolveRootPath(rootPath));
    }

    public List<ScriptMarketplaceEntry> listEntriesForQuery(String query) {
        if (scripts.isEmpty()) {
            return List.of();
        }
        String normalized = query == null ? null : query.toLowerCase(Locale.ROOT);
        return scripts
            .stream()
            .filter((script) -> script.matchesQuery(normalized))
            .map(SeedScript::entry)
            .toList();
    }

    public Optional<SeedScript> findById(String scriptId) {
        return scripts.stream().filter((script) -> script.id().equals(scriptId)).findFirst();
    }

    public record SeedScript(
        String id,
        String name,
        String description,
        String broadcaster,
        String sourceMediaType,
        String logoMediaType,
        Optional<Path> sourcePath,
        Optional<Path> logoPath,
        List<SeedAttachment> attachments,
        AtomicReference<byte[]> sourceBytes,
        AtomicReference<byte[]> logoBytes
    ) {
        ScriptMarketplaceEntry entry() {
            return new ScriptMarketplaceEntry(
                id,
                name,
                description,
                logoPath.isPresent() ? "/api/marketplace/scripts/" + id + "/logo" : null,
                broadcaster,
                0,
                false
            );
        }

        boolean matchesQuery(String normalized) {
            if (normalized == null || normalized.isBlank()) {
                return true;
            }
            String entryName = Optional.ofNullable(name).orElse("").toLowerCase(Locale.ROOT);
            String entryDescription = Optional.ofNullable(description).orElse("").toLowerCase(Locale.ROOT);
            return entryName.contains(normalized) || entryDescription.contains(normalized);
        }

        Optional<AssetContent> loadSource() {
            return sourcePath.flatMap((path) -> loadContent(sourceBytes, path, sourceMediaType));
        }

        Optional<AssetContent> loadLogo() {
            return logoPath.flatMap((path) -> loadContent(logoBytes, path, logoMediaType));
        }
    }

    public record SeedAttachment(String name, String mediaType, Path path, AtomicReference<byte[]> bytes) {}

    private List<SeedScript> loadScripts(Path rootPath) {
        if (rootPath == null) {
            return List.of();
        }
        if (!Files.isDirectory(rootPath)) {
            logger.warn("Marketplace script path {} is not a directory", rootPath);
            return List.of();
        }
        List<SeedScript> loaded = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(rootPath)) {
            for (Path scriptDir : stream) {
                if (!Files.isDirectory(scriptDir)) {
                    continue;
                }
                SeedScript script = loadScriptDirectory(scriptDir).orElse(null);
                if (script != null) {
                    loaded.add(script);
                }
            }
        } catch (IOException ex) {
            logger.warn("Failed to read marketplace script directory {}", rootPath, ex);
        }
        return List.copyOf(loaded);
    }

    private Optional<SeedScript> loadScriptDirectory(Path scriptDir) {
        ScriptSeedMetadata metadata = ScriptSeedMetadata.read(scriptDir.resolve(METADATA_FILENAME));
        if (metadata == null) {
            logger.warn("Skipping marketplace script {}, missing {}", scriptDir, METADATA_FILENAME);
            return Optional.empty();
        }
        if (metadata.name() == null || metadata.name().isBlank()) {
            logger.warn("Skipping marketplace script {}, missing name", scriptDir);
            return Optional.empty();
        }
        String sourceMediaType = detectMediaType(scriptDir.resolve(SOURCE_FILENAME), DEFAULT_SOURCE_MEDIA_TYPE);
        String logoMediaType = detectMediaType(scriptDir.resolve(LOGO_FILENAME), DEFAULT_LOGO_MEDIA_TYPE);
        String broadcaster = normalizeBroadcaster(metadata.broadcaster());

        Path sourcePath = resolveOptionalFile(scriptDir.resolve(SOURCE_FILENAME));
        Path logoPath = resolveOptionalFile(scriptDir.resolve(LOGO_FILENAME));
        if (sourcePath == null) {
            logger.warn("Skipping marketplace script {}, missing {}", scriptDir, SOURCE_FILENAME);
            return Optional.empty();
        }
        List<SeedAttachment> attachments = loadAttachments(scriptDir.resolve(ATTACHMENTS_DIR)).orElse(null);
        if (attachments == null) {
            return Optional.empty();
        }

        return Optional.of(
            new SeedScript(
                scriptDir.getFileName().toString(),
                metadata.name(),
                metadata.description(),
                broadcaster,
                sourceMediaType,
                logoMediaType,
                Optional.ofNullable(sourcePath),
                Optional.ofNullable(logoPath),
                attachments,
                new AtomicReference<>(),
                new AtomicReference<>()
            )
        );
    }

    private Optional<List<SeedAttachment>> loadAttachments(Path attachmentsDir) {
        if (!Files.isDirectory(attachmentsDir)) {
            return Optional.of(List.of());
        }
        List<SeedAttachment> attachments = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(attachmentsDir)) {
            for (Path attachment : stream) {
                if (Files.isRegularFile(attachment)) {
                    String name = attachment.getFileName().toString();
                    if (!seenNames.add(name)) {
                        logger.warn("Duplicate marketplace attachment name {}", name);
                        return Optional.empty();
                    }
                    String mediaType = detectAttachmentMediaType(attachment);
                    attachments.add(
                        new SeedAttachment(
                            name,
                            mediaType == null ? "application/octet-stream" : mediaType,
                            attachment,
                            new AtomicReference<>()
                        )
                    );
                }
            }
        } catch (IOException ex) {
            logger.warn("Failed to read marketplace attachments in {}", attachmentsDir, ex);
        }
        return Optional.of(List.copyOf(attachments));
    }

    private Path resolveRootPath(String rootPath) {
        if (rootPath != null && !rootPath.isBlank()) {
            return Path.of(rootPath);
        }
        Path docsPath = Path.of("doc", "marketplace-scripts");
        if (Files.isDirectory(docsPath)) {
            return docsPath;
        }
        return null;
    }

    private String detectAttachmentMediaType(Path attachment) {
        try {
            String mediaType = Files.probeContentType(attachment);
            if (
                mediaType != null &&
                !mediaType.isBlank() &&
                !"application/octet-stream".equals(mediaType) &&
                !"text/plain".equals(mediaType)
            ) {
                return mediaType;
            }
        } catch (IOException ex) {
            logger.warn("Failed to detect media type for {}", attachment, ex);
        }
        String filename = attachment.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = filename.lastIndexOf('.');
        if (dot > -1 && dot < filename.length() - 1) {
            String extension = filename.substring(dot + 1);
            String mapped = ATTACHMENT_MEDIA_TYPES.get(extension);
            if (mapped != null) {
                return mapped;
            }
        }
        return "application/octet-stream";
    }

    private String normalizeBroadcaster(String broadcaster) {
        if (broadcaster == null || broadcaster.isBlank()) {
            return "System";
        }
        return broadcaster;
    }

    private static Path resolveOptionalFile(Path path) {
        if (Files.isRegularFile(path)) {
            return path;
        }
        return null;
    }

    private static Optional<AssetContent> loadContent(
        AtomicReference<byte[]> cache,
        Path filePath,
        String mediaType
    ) {
        byte[] bytes = cache.get();
        if (bytes == null) {
            bytes = readBytes(filePath).orElse(null);
            if (bytes != null) {
                cache.set(bytes);
            }
        }
        if (bytes == null) {
            return Optional.empty();
        }
        return Optional.of(new AssetContent(bytes, mediaType));
    }

    private static Optional<byte[]> readBytes(Path filePath) {
        if (!Files.isRegularFile(filePath)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(filePath));
        } catch (IOException ex) {
            logger.warn("Failed to read marketplace script asset {}", filePath, ex);
            return Optional.empty();
        }
    }

    record ScriptSeedMetadata(String name, String description, String broadcaster) {
        static ScriptSeedMetadata read(Path path) {
            if (!Files.isRegularFile(path)) {
                return null;
            }
            try {
                String content = Files.readString(path);
                return JsonSupport.read(content, ScriptSeedMetadata.class);
            } catch (IOException ex) {
                logger.warn("Failed to read marketplace metadata {}", path, ex);
                return null;
            }
        }
    }

    private static final class JsonSupport {
        private static final AtomicReference<com.fasterxml.jackson.databind.ObjectMapper> OBJECT_MAPPER =
            new AtomicReference<>();

        private JsonSupport() {}

        static <T> T read(String payload, Class<T> type) throws IOException {
            return mapper().readValue(payload, type);
        }

        private static com.fasterxml.jackson.databind.ObjectMapper mapper() {
            com.fasterxml.jackson.databind.ObjectMapper mapper = OBJECT_MAPPER.get();
            if (mapper == null) {
                mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                OBJECT_MAPPER.set(mapper);
            }
            return mapper;
        }
    }

    private String detectMediaType(Path path, String fallback) {
        if (!Files.isRegularFile(path)) {
            return fallback;
        }
        try {
            String mediaType = Files.probeContentType(path);
            return mediaType == null ? fallback : mediaType;
        } catch (IOException ex) {
            logger.warn("Failed to detect media type for {}", path, ex);
            return fallback;
        }
    }
}
