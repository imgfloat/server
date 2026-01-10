package dev.kruhlmann.imgfloat.service;

import dev.kruhlmann.imgfloat.model.ScriptMarketplaceEntry;
import dev.kruhlmann.imgfloat.service.media.AssetContent;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

public final class DefaultMarketplaceScript {

    public static final String SCRIPT_ID = "imgfloat-default-rotating-logo";
    public static final String SCRIPT_NAME = "Rotating Imgfloat logo";
    public static final String SCRIPT_DESCRIPTION =
        "Renders the Imgfloat logo and rotates it every tick.";
    public static final String SCRIPT_BROADCASTER = "Imgfloat";
    public static final String ATTACHMENT_NAME = "Imgfloat logo";
    public static final String LOGO_URL = "/api/marketplace/scripts/" + SCRIPT_ID + "/logo";
    public static final String SOURCE_MEDIA_TYPE = "application/javascript";
    public static final String LOGO_MEDIA_TYPE = "image/png";

    private static final Logger logger = LoggerFactory.getLogger(DefaultMarketplaceScript.class);
    private static final String LOGO_RESOURCE = "static/img/brand.png";
    private static final String SOURCE_RESOURCE = "assets/default-marketplace-script.js";
    private static final AtomicReference<byte[]> LOGO_BYTES = new AtomicReference<>();
    private static final AtomicReference<byte[]> SOURCE_BYTES = new AtomicReference<>();

    private DefaultMarketplaceScript() {}

    public static boolean matches(String scriptId) {
        return SCRIPT_ID.equals(scriptId);
    }

    public static Optional<ScriptMarketplaceEntry> entryForQuery(String query) {
        if (query == null || query.isBlank()) {
            return Optional.of(entry());
        }
        String normalized = query.toLowerCase(Locale.ROOT);
        if (
            SCRIPT_NAME.toLowerCase(Locale.ROOT).contains(normalized) ||
            SCRIPT_DESCRIPTION.toLowerCase(Locale.ROOT).contains(normalized)
        ) {
            return Optional.of(entry());
        }
        return Optional.empty();
    }

    public static ScriptMarketplaceEntry entry() {
        return new ScriptMarketplaceEntry(
            SCRIPT_ID,
            SCRIPT_NAME,
            SCRIPT_DESCRIPTION,
            LOGO_URL,
            SCRIPT_BROADCASTER
        );
    }

    public static Optional<AssetContent> logoContent() {
        return loadContent(LOGO_BYTES, LOGO_RESOURCE, LOGO_MEDIA_TYPE);
    }

    public static Optional<AssetContent> sourceContent() {
        return loadContent(SOURCE_BYTES, SOURCE_RESOURCE, SOURCE_MEDIA_TYPE);
    }

    public static Optional<AssetContent> attachmentContent() {
        return logoContent();
    }

    private static Optional<AssetContent> loadContent(
        AtomicReference<byte[]> cache,
        String resourcePath,
        String mediaType
    ) {
        byte[] bytes = cache.get();
        if (bytes == null) {
            bytes = readBytes(resourcePath).orElse(null);
            if (bytes != null) {
                cache.set(bytes);
            }
        }
        if (bytes == null) {
            return Optional.empty();
        }
        return Optional.of(new AssetContent(bytes, mediaType));
    }

    private static Optional<byte[]> readBytes(String resourcePath) {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            logger.warn("Default marketplace resource {} is missing", resourcePath);
            return Optional.empty();
        }
        try (InputStream input = resource.getInputStream()) {
            return Optional.of(input.readAllBytes());
        } catch (IOException ex) {
            logger.warn("Failed to read default marketplace resource {}", resourcePath, ex);
            return Optional.empty();
        }
    }
}
