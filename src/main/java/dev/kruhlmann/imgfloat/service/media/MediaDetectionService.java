package dev.kruhlmann.imgfloat.service.media;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaDetectionService {

    private static final Logger logger = LoggerFactory.getLogger(MediaDetectionService.class);

    public Optional<String> detectAllowedMediaType(MultipartFile file, byte[] bytes) {
        Optional<String> detected = detectMediaType(bytes)
            .map(MediaTypeRegistry::normalizeJavaScriptMediaType)
            .filter(MediaTypeRegistry::isSupportedMediaType);

        if (detected.isPresent()) {
            return detected;
        }

        Optional<String> declared = Optional.ofNullable(file.getContentType())
            .map(MediaTypeRegistry::normalizeJavaScriptMediaType)
            .filter(MediaTypeRegistry::isSupportedMediaType);
        if (declared.isPresent()) {
            return declared;
        }

        return Optional.ofNullable(file.getOriginalFilename())
            .map((name) -> name.replaceAll("^.*\\.", "").toLowerCase(Locale.ROOT))
            .flatMap(MediaTypeRegistry::mediaTypeForExtension)
            .filter(MediaTypeRegistry::isSupportedMediaType);
    }

    private Optional<String> detectMediaType(byte[] bytes) {
        try (var stream = new ByteArrayInputStream(bytes)) {
            if (ApngDetector.isApng(bytes)) {
                return Optional.of("image/apng");
            }
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
        return MediaTypeRegistry.isSupportedMediaType(mediaType);
    }

    public static boolean isInlineDisplayType(String mediaType) {
        return (
            mediaType != null &&
            (mediaType.startsWith("image/") || mediaType.startsWith("video/") || mediaType.startsWith("audio/"))
        );
    }
}
