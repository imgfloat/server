package dev.kruhlmann.imgfloat.service.media;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaDetectionService {

    private static final Logger logger = LoggerFactory.getLogger(MediaDetectionService.class);
    private static final Map<String, String> EXTENSION_TYPES = Map.ofEntries(
        Map.entry("png", "image/png"),
        Map.entry("jpg", "image/jpeg"),
        Map.entry("jpeg", "image/jpeg"),
        Map.entry("gif", "image/gif"),
        Map.entry("webp", "image/webp"),
        Map.entry("mp4", "video/mp4"),
        Map.entry("webm", "video/webm"),
        Map.entry("mov", "video/quicktime"),
        Map.entry("mp3", "audio/mpeg"),
        Map.entry("wav", "audio/wav"),
        Map.entry("ogg", "audio/ogg"),
        Map.entry("glb", "model/gltf-binary"),
        Map.entry("gltf", "model/gltf+json"),
        Map.entry("obj", "model/obj"),
        Map.entry("js", "application/javascript"),
        Map.entry("mjs", "text/javascript")
    );
    private static final Set<String> ALLOWED_MEDIA_TYPES = Set.copyOf(EXTENSION_TYPES.values());

    public Optional<String> detectAllowedMediaType(MultipartFile file, byte[] bytes) {
        Optional<String> detected = detectMediaType(bytes)
            .map(MediaDetectionService::normalizeJavaScriptMediaType)
            .filter(MediaDetectionService::isAllowedMediaType);

        if (detected.isPresent()) {
            return detected;
        }

        Optional<String> declared = Optional.ofNullable(file.getContentType())
            .map(MediaDetectionService::normalizeJavaScriptMediaType)
            .filter(MediaDetectionService::isAllowedMediaType);
        if (declared.isPresent()) {
            return declared;
        }

        return Optional.ofNullable(file.getOriginalFilename())
            .map((name) -> name.replaceAll("^.*\\.", "").toLowerCase())
            .map(EXTENSION_TYPES::get)
            .filter(MediaDetectionService::isAllowedMediaType);
    }

    private Optional<String> detectMediaType(byte[] bytes) {
        try (var stream = new ByteArrayInputStream(bytes)) {
            String guessed = URLConnection.guessContentTypeFromStream(stream);
            if (guessed != null && !guessed.isBlank()) {
                return Optional.of(guessed);
            }
        } catch (IOException e) {
            logger.warn("Unable to detect content type from stream", e);
        }

        return Optional.empty();
    }

    public static boolean isAllowedMediaType(String mediaType) {
        String normalized = normalizeJavaScriptMediaType(mediaType);
        return normalized != null && ALLOWED_MEDIA_TYPES.contains(normalized.toLowerCase());
    }

    public static boolean isInlineDisplayType(String mediaType) {
        return (
            mediaType != null &&
            (mediaType.startsWith("image/") || mediaType.startsWith("video/") || mediaType.startsWith("audio/"))
        );
    }

    private static String normalizeJavaScriptMediaType(String mediaType) {
        if (mediaType == null) {
            return null;
        }
        String normalized = mediaType.toLowerCase(Locale.ROOT);
        if (normalized.contains("javascript") || normalized.contains("ecmascript")) {
            return "application/javascript";
        }
        return mediaType;
    }
}
