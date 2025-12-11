package dev.kruhlmann.imgfloat.service.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.util.Optional;

@Service
public class MediaDetectionService {
    private static final Logger logger = LoggerFactory.getLogger(MediaDetectionService.class);

    public String detectMediaType(MultipartFile file, byte[] bytes) {
        String contentType = Optional.ofNullable(file.getContentType()).orElse("application/octet-stream");
        if (!"application/octet-stream".equals(contentType) && !contentType.isBlank()) {
            return contentType;
        }

        try (var stream = new ByteArrayInputStream(bytes)) {
            String guessed = URLConnection.guessContentTypeFromStream(stream);
            if (guessed != null && !guessed.isBlank()) {
                return guessed;
            }
        } catch (IOException e) {
            logger.warn("Unable to detect content type from stream", e);
        }

        return Optional.ofNullable(file.getOriginalFilename())
                .map(name -> name.replaceAll("^.*\\.", "").toLowerCase())
                .map(ext -> switch (ext) {
                    case "png" -> "image/png";
                    case "jpg", "jpeg" -> "image/jpeg";
                    case "gif" -> "image/gif";
                    case "mp4" -> "video/mp4";
                    case "webm" -> "video/webm";
                    case "mov" -> "video/quicktime";
                    case "mp3" -> "audio/mpeg";
                    case "wav" -> "audio/wav";
                    case "ogg" -> "audio/ogg";
                    default -> "application/octet-stream";
                })
                .orElse("application/octet-stream");
    }
}
