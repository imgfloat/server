package dev.kruhlmann.imgfloat.service;

import dev.kruhlmann.imgfloat.model.Settings;
import dev.kruhlmann.imgfloat.repository.SettingsRepository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SettingsService {
    private static final Logger logger = LoggerFactory.getLogger(SettingsService.class);

    private final SettingsRepository repo;
    private final ObjectMapper objectMapper;

    public SettingsService(SettingsRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initDefaults() {
        if (repo.existsById(1)) {
            return;
        }
        Settings s = Settings.defaults();
        logSettings("Initializing default settings", s);
        repo.save(s);
    }

    public Settings get() {
        return repo.findById(1).orElseThrow();
    }

    public Settings save(Settings settings) {
        settings.setId(1);
        logSettings("Saving settings", settings);
        return repo.save(settings);
    }

    public void logSettings(String msg, Settings settings) {
        try {
            logger.info("{}:\n{}",
                msg,
                objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(settings)
            );
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize settings", e);
        }
    }
}
