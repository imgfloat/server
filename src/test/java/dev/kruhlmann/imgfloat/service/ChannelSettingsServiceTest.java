package dev.kruhlmann.imgfloat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import dev.kruhlmann.imgfloat.model.api.request.CanvasSettingsRequest;
import dev.kruhlmann.imgfloat.model.api.request.ChannelScriptSettingsRequest;
import dev.kruhlmann.imgfloat.model.db.imgfloat.Channel;
import dev.kruhlmann.imgfloat.model.db.imgfloat.Settings;
import dev.kruhlmann.imgfloat.repository.ChannelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

class ChannelSettingsServiceTest {

    private ChannelRepository channelRepository;
    private SettingsService settingsService;
    private SimpMessagingTemplate messagingTemplate;
    private AuditLogService auditLogService;
    private ChannelSettingsService service;

    private final ConcurrentHashMap<String, Channel> channels = new ConcurrentHashMap<>();

    @BeforeEach
    void setup() {
        channelRepository = mock(ChannelRepository.class);
        settingsService = mock(SettingsService.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        auditLogService = mock(AuditLogService.class);

        when(settingsService.get()).thenReturn(Settings.defaults());
        when(channelRepository.findById(anyString())).thenAnswer(inv ->
            Optional.ofNullable(channels.get(inv.getArgument(0))));
        when(channelRepository.save(any(Channel.class))).thenAnswer(inv -> {
            Channel ch = inv.getArgument(0);
            channels.put(ch.getBroadcaster(), ch);
            return ch;
        });

        service = new ChannelSettingsService(channelRepository, settingsService, messagingTemplate, auditLogService);
    }

    // --- canvas settings ---

    @Test
    void getCanvasSettingsReturnsDefaults() {
        CanvasSettingsRequest result = service.getCanvasSettings("caster");
        assertThat(result.getWidth()).isGreaterThan(0);
        assertThat(result.getHeight()).isGreaterThan(0);
    }

    @Test
    void updateCanvasPersistsAndBroadcasts() {
        CanvasSettingsRequest req = new CanvasSettingsRequest(1920, 1080, -12.0);
        CanvasSettingsRequest result = service.updateCanvasSettings("caster", req, "caster");

        assertThat(result.getWidth()).isEqualTo(1920);
        assertThat(result.getHeight()).isEqualTo(1080);
        assertThat(result.getMaxVolumeDb()).isEqualTo(-12.0);
        verify(messagingTemplate).convertAndSend(anyString(), any(Object.class));
        Channel channel = channels.get("caster");
        assertThat(channel.getMaxVolumeDb()).isEqualTo(-12.0);
    }

    @Test
    void updateCanvasRecordsAuditEntry() {
        service.updateCanvasSettings("caster", new CanvasSettingsRequest(1280, 720, null), "admin");
        verify(auditLogService).recordEntry(eq("caster"), eq("admin"), eq("CANVAS_UPDATED"), anyString());
    }

    @Test
    void updateCanvasNoAuditEntryWhenNothingChanged() {
        service.updateCanvasSettings("caster", new CanvasSettingsRequest(1280, 720, null), "admin");
        clearInvocations(auditLogService);
        // Second call with same values — nothing changed, no audit
        service.updateCanvasSettings("caster", new CanvasSettingsRequest(1280, 720, null), "admin");
        verify(auditLogService, never()).recordEntry(any(), any(), any(), any());
    }

    @Test
    void updateCanvasRejectsZeroWidth() {
        assertThatThrownBy(() -> service.updateCanvasSettings("caster", new CanvasSettingsRequest(0, 720, null), "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("width");
    }

    @Test
    void updateCanvasRejectsInvalidVolumeDb() {
        assertThatThrownBy(() -> service.updateCanvasSettings("caster", new CanvasSettingsRequest(1280, 720, 5.0), "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("volume");
    }

    @Test
    void updateCanvasAcceptsBoundaryVolumeValues() {
        // -60 and 0 are the inclusive bounds
        assertThat(service.updateCanvasSettings("caster", new CanvasSettingsRequest(1280, 720, -60.0), "admin")
                .getMaxVolumeDb()).isEqualTo(-60.0);
        assertThat(service.updateCanvasSettings("caster", new CanvasSettingsRequest(1280, 720, 0.0), "admin")
                .getMaxVolumeDb()).isEqualTo(0.0);
    }

    // --- script settings ---

    @Test
    void getScriptSettingsReturnsDefaults() {
        ChannelScriptSettingsRequest result = service.getChannelScriptSettings("caster");
        assertThat(result).isNotNull();
    }

    @Test
    void updateScriptSettingsPersistsChanges() {
        ChannelScriptSettingsRequest req = new ChannelScriptSettingsRequest(true, false, true);
        ChannelScriptSettingsRequest result = service.updateChannelScriptSettings("caster", req, "admin");

        assertThat(result.isAllowChannelEmotesForAssets()).isTrue();
        assertThat(result.isAllowSevenTvEmotesForAssets()).isFalse();
        assertThat(result.isAllowScriptChatAccess()).isTrue();
    }

    @Test
    void updateScriptSettingsRecordsAuditEntry() {
        service.updateChannelScriptSettings("caster", new ChannelScriptSettingsRequest(true, false, true), "admin");
        verify(auditLogService).recordEntry(eq("caster"), eq("admin"), eq("SCRIPT_SETTINGS_UPDATED"), anyString());
    }

    @Test
    void updateScriptSettingsNoAuditWhenNothingChanged() {
        service.updateChannelScriptSettings("caster", new ChannelScriptSettingsRequest(false, false, false), "admin");
        clearInvocations(auditLogService);
        service.updateChannelScriptSettings("caster", new ChannelScriptSettingsRequest(false, false, false), "admin");
        verify(auditLogService, never()).recordEntry(any(), any(), any(), any());
    }
}
