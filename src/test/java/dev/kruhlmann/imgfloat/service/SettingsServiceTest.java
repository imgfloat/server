package dev.kruhlmann.imgfloat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kruhlmann.imgfloat.model.db.imgfloat.AudioAsset;
import dev.kruhlmann.imgfloat.model.db.imgfloat.Settings;
import dev.kruhlmann.imgfloat.model.db.imgfloat.VisualAsset;
import dev.kruhlmann.imgfloat.repository.AudioAssetRepository;
import dev.kruhlmann.imgfloat.repository.SettingsRepository;
import dev.kruhlmann.imgfloat.repository.VisualAssetRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SettingsServiceTest {

    private SettingsRepository repo;
    private VisualAssetRepository visualAssetRepository;
    private AudioAssetRepository audioAssetRepository;
    private SettingsService service;

    @BeforeEach
    void setup() {
        repo = mock(SettingsRepository.class);
        visualAssetRepository = mock(VisualAssetRepository.class);
        audioAssetRepository = mock(AudioAssetRepository.class);
        when(visualAssetRepository.findAll()).thenReturn(List.of());
        when(audioAssetRepository.findAll()).thenReturn(List.of());
        service = new SettingsService(repo, visualAssetRepository, audioAssetRepository, new ObjectMapper());
    }

    @Test
    void initDefaultsCreatesSettingsWhenAbsent() {
        when(repo.existsById(1)).thenReturn(false);
        service.initDefaults();
        verify(repo).save(any(Settings.class));
    }

    @Test
    void initDefaultsSkipsWhenAlreadyPresent() {
        when(repo.existsById(1)).thenReturn(true);
        service.initDefaults();
        verify(repo, never()).save(any());
    }

    @Test
    void getReturnsStoredSettings() {
        Settings stored = Settings.defaults();
        stored.setId(1);
        when(repo.findById(1)).thenReturn(Optional.of(stored));
        assertThat(service.get()).isSameAs(stored);
    }

    @Test
    void saveClampsVisualAssetSpeedToNewRange() {
        VisualAsset visual = new VisualAsset();
        visual.setSpeed(5.0); // exceeds max
        visual.setAudioVolume(0.5);
        when(visualAssetRepository.findAll()).thenReturn(List.of(visual));

        Settings settings = Settings.defaults();
        settings.setMaxAssetPlaybackSpeedFraction(2.0);
        settings.setMinAssetPlaybackSpeedFraction(0.1);
        settings.setId(1);
        when(repo.save(any())).thenReturn(settings);

        service.save(settings);

        verify(visualAssetRepository).saveAll(any());
        assertThat(visual.getSpeed()).isEqualTo(2.0);
    }

    @Test
    void saveClampsAudioAssetPitchToNewRange() {
        AudioAsset audio = new AudioAsset();
        audio.setAudioSpeed(1.0);
        audio.setAudioPitch(3.0); // exceeds max
        audio.setAudioVolume(0.5);
        when(audioAssetRepository.findAll()).thenReturn(List.of(audio));

        Settings settings = Settings.defaults();
        settings.setMaxAssetAudioPitchFraction(2.0);
        settings.setMinAssetAudioPitchFraction(0.5);
        settings.setId(1);
        when(repo.save(any())).thenReturn(settings);

        service.save(settings);

        verify(audioAssetRepository).saveAll(any());
        assertThat(audio.getAudioPitch()).isEqualTo(2.0);
    }
}
