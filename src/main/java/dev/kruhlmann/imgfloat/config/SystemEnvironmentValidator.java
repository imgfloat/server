package dev.kruhlmann.imgfloat.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;

import java.util.Locale;

@Component
public class SystemEnvironmentValidator {
    private static final Logger log = LoggerFactory.getLogger(SystemEnvironmentValidator.class);

    @Value("${spring.security.oauth2.client.registration.twitch.client-id:#{null}}")
    private String twitchClientId;
    @Value("${spring.security.oauth2.client.registration.twitch.client-secret:#{null}}")
    private String twitchClientSecret;
    @Value("${spring.servlet.multipart.max-file-size:#{null}}")
    private String springMaxFileSize;
    @Value("${spring.servlet.multipart.max-request-size:#{null}}")
    private String springMaxRequestSize;
    @Value("${IMGFLOAT_ASSETS_PATH:#{null}}")
    private String assetsPath;
    @Value("${IMGFLOAT_PREVIEWS_PATH:#{null}}")
    private String previewsPath;
    @Value("${IMGFLOAT_DB_PATH}")
    private String dbPath;
    @Value("${IMGFLOAT_MAX_SPEED}")
    private double maxSpeed;
    @Value("${IMGFLOAT_MIN_AUDIO_SPEED}")
    private double minAudioSpeed;
    @Value("${IMGFLOAT_MAX_AUDIO_SPEED}")
    private double maxAudioSpeed;
    @Value("${IMGFLOAT_MIN_AUDIO_PITCH}")
    private double minAudioPitch;
    @Value("${IMGFLOAT_MAX_AUDIO_PITCH}")
    private double maxAudioPitch;
    @Value("${IMGFLOAT_MAX_AUDIO_VOLUME}")
    private double maxAudioVolume;

    private long maxUploadBytes;
    private long maxRequestBytes;

    @PostConstruct
    public void validate() {
        StringBuilder missing = new StringBuilder();

        maxUploadBytes = DataSize.parse(springMaxFileSize).toBytes();
        maxRequestBytes = DataSize.parse(springMaxRequestSize).toBytes();
        checkUnsignedNumeric(maxUploadBytes, "SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE", missing);
        checkUnsignedNumeric(maxRequestBytes, "SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE", missing);
        checkUnsignedNumeric(maxSpeed, "IMGFLOAT_MAX_SPEED", missing);;
        checkUnsignedNumeric(minAudioSpeed, "IMGFLOAT_MIN_AUDIO_SPEED", missing);;
        checkUnsignedNumeric(maxAudioSpeed, "IMGFLOAT_MAX_AUDIO_SPEED", missing);;
        checkUnsignedNumeric(minAudioPitch, "IMGFLOAT_MIN_AUDIO_PITCH", missing);;
        checkUnsignedNumeric(maxAudioPitch, "IMGFLOAT_MAX_AUDIO_PITCH", missing);;
        checkUnsignedNumeric(maxAudioVolume, "IMGFLOAT_MAX_AUDIO_VOLUME", missing);;
        checkString(twitchClientId, "TWITCH_CLIENT_ID", missing);
        checkString(dbPath, "IMGFLOAT_DB_PATH", missing);
        checkString(twitchClientSecret, "TWITCH_CLIENT_SECRET", missing);
        checkString(assetsPath, "IMGFLOAT_ASSETS_PATH", missing);
        checkString(previewsPath, "IMGFLOAT_PREVIEWS_PATH", missing);

        if (missing.length() > 0) {
            throw new IllegalStateException(
                "Missing or invalid environment variables:\n" + missing
            );
        }

        log.info("Environment validation successful.");
        log.info("Configuration:");
        log.info(" - TWITCH_CLIENT_ID: {}", redact(twitchClientId));
        log.info(" - TWITCH_CLIENT_SECRET: {}", redact(twitchClientSecret));
        log.info(" - SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE: {} ({} bytes)",
                springMaxFileSize, maxUploadBytes);
        log.info(" - SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE: {} ({} bytes)",
                springMaxRequestSize, maxRequestBytes);
        log.info(" - IMGFLOAT_ASSETS_PATH: {}", assetsPath);
        log.info(" - IMGFLOAT_PREVIEWS_PATH: {}", previewsPath);
        log.info(" - IMGFLOAT_DB_PATH: {}", dbPath);
        log.info(" - IMGFLOAT_MAX_SPEED: {}", maxSpeed);
        log.info(" - IMGFLOAT_MIN_AUDIO_SPEED: {}", minAudioSpeed);
        log.info(" - IMGFLOAT_MAX_AUDIO_SPEED: {}", maxAudioSpeed);
        log.info(" - IMGFLOAT_MIN_AUDIO_PITCH: {}", minAudioPitch);
        log.info(" - IMGFLOAT_MAX_AUDIO_PITCH: {}", maxAudioPitch);
        log.info(" - IMGFLOAT_MAX_AUDIO_VOLUME: {}", maxAudioVolume);
    }

    private void checkString(String value, String name, StringBuilder missing) {
        if (!StringUtils.hasText(value) || "changeme".equalsIgnoreCase(value.trim())) {
            missing.append(" - ").append(name).append("\n");
        }
    }

    private <T extends Number> void checkUnsignedNumeric(T value, String name, StringBuilder missing) {
        if (value == null || value.doubleValue() <= 0) {
            missing.append(" - ").append(name).append('\n');
        }
    }

    private String redact(String value) {
        if (!StringUtils.hasText(value)) return "(missing)";
        if (value.length() <= 6) return "******";
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }
}
