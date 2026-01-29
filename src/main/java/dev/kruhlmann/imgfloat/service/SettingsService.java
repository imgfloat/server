package dev.kruhlmann.imgfloat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kruhlmann.imgfloat.model.db.imgfloat.AudioAsset;
import dev.kruhlmann.imgfloat.model.db.imgfloat.Settings;
import dev.kruhlmann.imgfloat.model.db.imgfloat.VisualAsset;
import dev.kruhlmann.imgfloat.repository.AudioAssetRepository;
import dev.kruhlmann.imgfloat.repository.SettingsRepository;
import dev.kruhlmann.imgfloat.repository.VisualAssetRepository;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SettingsService {

    private static final Logger logger = LoggerFactory.getLogger(SettingsService.class);

    private final SettingsRepository repo;
    private final VisualAssetRepository visualAssetRepository;
    private final AudioAssetRepository audioAssetRepository;
    private final ObjectMapper objectMapper;

    public SettingsService(
        SettingsRepository repo,
        VisualAssetRepository visualAssetRepository,
        AudioAssetRepository audioAssetRepository,
        ObjectMapper objectMapper
    ) {
        this.repo = repo;
        this.visualAssetRepository = visualAssetRepository;
        this.audioAssetRepository = audioAssetRepository;
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
        Settings savedSettings = repo.save(settings);
        clampAssetsToSettings(savedSettings);
        return savedSettings;
    }

    public void updateLastEmoteSyncAt(Instant timestamp) {
        Settings settings = get();
        settings.setLastEmoteSyncAt(timestamp);
        repo.save(settings);
    }

    public void logSettings(String msg, Settings settings) {
        try {
            logger.info("{}:\n{}", msg, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(settings));
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize settings", e);
        }
    }

    private void clampAssetsToSettings(Settings settings) {
        double minSpeed = settings.getMinAssetPlaybackSpeedFraction();
        double maxSpeed = settings.getMaxAssetPlaybackSpeedFraction();
        double minPitch = settings.getMinAssetAudioPitchFraction();
        double maxPitch = settings.getMaxAssetAudioPitchFraction();
        double minVolume = settings.getMinAssetVolumeFraction();
        double maxVolume = settings.getMaxAssetVolumeFraction();

        List<VisualAsset> visualsToUpdate = new ArrayList<>();
        for (VisualAsset visual : visualAssetRepository.findAll()) {
            boolean changed = false;
            double speed = visual.getSpeed();
            double clampedSpeed = clamp(speed, minSpeed, maxSpeed);
            if (Double.compare(speed, clampedSpeed) != 0) {
                visual.setSpeed(clampedSpeed);
                changed = true;
            }
            double volume = visual.getAudioVolume();
            double clampedVolume = clamp(volume, minVolume, maxVolume);
            if (Double.compare(volume, clampedVolume) != 0) {
                visual.setAudioVolume(clampedVolume);
                changed = true;
            }
            if (changed) {
                visualsToUpdate.add(visual);
            }
        }

        List<AudioAsset> audioToUpdate = new ArrayList<>();
        for (AudioAsset audio : audioAssetRepository.findAll()) {
            boolean changed = false;
            double speed = audio.getAudioSpeed();
            double clampedSpeed = clamp(speed, minSpeed, maxSpeed);
            if (Double.compare(speed, clampedSpeed) != 0) {
                audio.setAudioSpeed(clampedSpeed);
                changed = true;
            }
            double pitch = audio.getAudioPitch();
            double clampedPitch = clamp(pitch, minPitch, maxPitch);
            if (Double.compare(pitch, clampedPitch) != 0) {
                audio.setAudioPitch(clampedPitch);
                changed = true;
            }
            double volume = audio.getAudioVolume();
            double clampedVolume = clamp(volume, minVolume, maxVolume);
            if (Double.compare(volume, clampedVolume) != 0) {
                audio.setAudioVolume(clampedVolume);
                changed = true;
            }
            if (changed) {
                audioToUpdate.add(audio);
            }
        }

        if (!visualsToUpdate.isEmpty()) {
            visualAssetRepository.saveAll(visualsToUpdate);
        }
        if (!audioToUpdate.isEmpty()) {
            audioAssetRepository.saveAll(audioToUpdate);
        }

        if (!visualsToUpdate.isEmpty() || !audioToUpdate.isEmpty()) {
            logger.info(
                "Normalized {} visual assets and {} audio assets to new settings ranges",
                visualsToUpdate.size(),
                audioToUpdate.size()
            );
        }
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
