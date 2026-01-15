package dev.kruhlmann.imgfloat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.kruhlmann.imgfloat.model.Asset;
import dev.kruhlmann.imgfloat.model.AssetView;
import dev.kruhlmann.imgfloat.model.AudioAsset;
import dev.kruhlmann.imgfloat.model.Channel;
import dev.kruhlmann.imgfloat.model.ScriptAsset;
import dev.kruhlmann.imgfloat.model.Settings;
import dev.kruhlmann.imgfloat.model.TransformRequest;
import dev.kruhlmann.imgfloat.model.VisibilityRequest;
import dev.kruhlmann.imgfloat.repository.AssetRepository;
import dev.kruhlmann.imgfloat.repository.AudioAssetRepository;
import dev.kruhlmann.imgfloat.repository.ChannelRepository;
import dev.kruhlmann.imgfloat.repository.MarketplaceScriptHeartRepository;
import dev.kruhlmann.imgfloat.repository.ScriptAssetRepository;
import dev.kruhlmann.imgfloat.repository.ScriptAssetAttachmentRepository;
import dev.kruhlmann.imgfloat.repository.ScriptAssetFileRepository;
import dev.kruhlmann.imgfloat.repository.VisualAssetRepository;
import dev.kruhlmann.imgfloat.service.AssetStorageService;
import dev.kruhlmann.imgfloat.service.AuditLogService;
import dev.kruhlmann.imgfloat.service.ChannelDirectoryService;
import dev.kruhlmann.imgfloat.service.MarketplaceScriptSeedLoader;
import dev.kruhlmann.imgfloat.service.SettingsService;
import dev.kruhlmann.imgfloat.service.media.MediaDetectionService;
import dev.kruhlmann.imgfloat.service.media.MediaOptimizationService;
import dev.kruhlmann.imgfloat.service.media.MediaPreviewService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class ChannelDirectoryServiceTest {

    private ChannelDirectoryService service;
    private SimpMessagingTemplate messagingTemplate;
    private ChannelRepository channelRepository;
    private AssetRepository assetRepository;
    private VisualAssetRepository visualAssetRepository;
    private AudioAssetRepository audioAssetRepository;
    private ScriptAssetRepository scriptAssetRepository;
    private ScriptAssetAttachmentRepository scriptAssetAttachmentRepository;
    private ScriptAssetFileRepository scriptAssetFileRepository;
    private MarketplaceScriptHeartRepository marketplaceScriptHeartRepository;
    private SettingsService settingsService;
    private MarketplaceScriptSeedLoader marketplaceScriptSeedLoader;
    private AuditLogService auditLogService;

    @BeforeEach
    void setup() throws Exception {
        messagingTemplate = mock(SimpMessagingTemplate.class);
        channelRepository = mock(ChannelRepository.class);
        assetRepository = mock(AssetRepository.class);
        visualAssetRepository = mock(VisualAssetRepository.class);
        audioAssetRepository = mock(AudioAssetRepository.class);
        scriptAssetRepository = mock(ScriptAssetRepository.class);
        scriptAssetAttachmentRepository = mock(ScriptAssetAttachmentRepository.class);
        scriptAssetFileRepository = mock(ScriptAssetFileRepository.class);
        marketplaceScriptHeartRepository = mock(MarketplaceScriptHeartRepository.class);
        auditLogService = mock(AuditLogService.class);
        when(marketplaceScriptHeartRepository.countByScriptIds(any())).thenReturn(List.of());
        when(marketplaceScriptHeartRepository.findByUsernameAndScriptIdIn(anyString(), any())).thenReturn(List.of());
        settingsService = mock(SettingsService.class);
        when(settingsService.get()).thenReturn(Settings.defaults());
        setupInMemoryPersistence();
        Path assetRoot = Files.createTempDirectory("imgfloat-assets-test");
        Path previewRoot = Files.createTempDirectory("imgfloat-previews-test");
        AssetStorageService assetStorageService = new AssetStorageService(assetRoot.toString(), previewRoot.toString());
        MediaPreviewService mediaPreviewService = new MediaPreviewService();
        MediaOptimizationService mediaOptimizationService = new MediaOptimizationService(mediaPreviewService);
        MediaDetectionService mediaDetectionService = new MediaDetectionService();
        long uploadLimitBytes = 5_000_000L;
        Path marketplaceRoot = Files.createTempDirectory("imgfloat-marketplace-test");
        Path scriptRoot = marketplaceRoot.resolve("rotating-logo");
        Files.createDirectories(scriptRoot);
        Files.createDirectories(scriptRoot.resolve("attachments"));
        Files.writeString(
            scriptRoot.resolve("metadata.json"),
            """
            {
              "name": "Rotating logo",
              "description": "Renders the Imgfloat logo and rotates it every tick."
            }
            """
        );
        Files.writeString(scriptRoot.resolve("source.js"), "console.log('seeded');");
        Files.write(scriptRoot.resolve("logo.png"), samplePng());
        Files.write(scriptRoot.resolve("attachments/rotate.png"), samplePng());
        marketplaceScriptSeedLoader = new MarketplaceScriptSeedLoader(marketplaceRoot.toString());
        service = new ChannelDirectoryService(
            channelRepository,
            assetRepository,
            visualAssetRepository,
            audioAssetRepository,
            scriptAssetRepository,
            scriptAssetAttachmentRepository,
            scriptAssetFileRepository,
            marketplaceScriptHeartRepository,
            messagingTemplate,
            assetStorageService,
            mediaDetectionService,
            mediaOptimizationService,
            settingsService,
            uploadLimitBytes,
            marketplaceScriptSeedLoader,
            auditLogService
        );
    }

    @Test
    void createsAssetsAndBroadcastsEvents() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", samplePng());

        Optional<AssetView> created = service.createAsset("caster", file, "caster");
        assertThat(created).isPresent();
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(
            org.mockito.ArgumentMatchers.contains("/topic/channel/caster"),
            captor.capture()
        );
    }

    @Test
    void updatesTransformAndVisibility() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", samplePng());
        String channel = "caster";
        String id = service.createAsset(channel, file, "caster").orElseThrow().id();

        TransformRequest transform = validTransform();

        assertThat(service.updateTransform(channel, id, transform, "caster")).isPresent();

        VisibilityRequest visibilityRequest = new VisibilityRequest();
        visibilityRequest.setHidden(false);
        assertThat(service.updateVisibility(channel, id, visibilityRequest, "caster")).isPresent();
    }

    @Test
    void rejectsInvalidTransformDimensions() throws Exception {
        String channel = "caster";
        String id = createSampleAsset(channel);

        TransformRequest transform = validTransform();
        transform.setWidth(0.0);

        assertThatThrownBy(() -> service.updateTransform(channel, id, transform, "caster"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Canvas width out of range");
    }

    @Test
    void rejectsOutOfRangePlaybackValues() throws Exception {
        String channel = "caster";
        String id = createSampleAsset(channel);

        TransformRequest speedTransform = validTransform();
        speedTransform.setSpeed(5.0);

        assertThatThrownBy(() -> service.updateTransform(channel, id, speedTransform, "caster"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Speed out of range");

        TransformRequest volumeTransform = validTransform();
        volumeTransform.setAudioVolume(6.5);

        assertThatThrownBy(() -> service.updateTransform(channel, id, volumeTransform, "caster"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Audio volume out of range");
    }

    @Test
    void appliesBoundaryValues() throws Exception {
        String channel = "caster";
        String id = createSampleAsset(channel);

        TransformRequest transform = validTransform();
        transform.setSpeed(0.1);
        transform.setAudioVolume(0.01);
        transform.setOrder(1);

        AssetView view = service.updateTransform(channel, id, transform, "caster").orElseThrow();

        assertThat(view.speed()).isEqualTo(0.1);
        assertThat(view.audioVolume()).isEqualTo(0.01);
        assertThat(assetRepository.findById(id).orElseThrow().getDisplayOrder()).isEqualTo(1);
    }

    @Test
    void includesDefaultMarketplaceScript() {
        when(scriptAssetRepository.findByIsPublicTrue()).thenReturn(List.of());

        List<dev.kruhlmann.imgfloat.model.ScriptMarketplaceEntry> entries = service.listMarketplaceScripts(null, null);

        assertThat(entries)
            .anyMatch((entry) -> "rotating-logo".equals(entry.id()));
    }

    private byte[] samplePng() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private String createSampleAsset(String channel) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", samplePng());
        return service.createAsset(channel, file, "caster").orElseThrow().id();
    }

    private TransformRequest validTransform() {
        TransformRequest transform = new TransformRequest();
        transform.setX(10.0);
        transform.setY(20.0);
        transform.setWidth(200.0);
        transform.setHeight(150.0);
        transform.setRotation(45.0);
        return transform;
    }

    private void setupInMemoryPersistence() {
        Map<String, Channel> channels = new ConcurrentHashMap<>();
        Map<String, Asset> assets = new ConcurrentHashMap<>();
        Map<String, dev.kruhlmann.imgfloat.model.VisualAsset> visualAssets = new ConcurrentHashMap<>();
        Map<String, AudioAsset> audioAssets = new ConcurrentHashMap<>();
        Map<String, ScriptAsset> scriptAssets = new ConcurrentHashMap<>();

        when(channelRepository.findById(anyString())).thenAnswer((invocation) ->
            Optional.ofNullable(channels.get(invocation.getArgument(0)))
        );
        when(channelRepository.save(any(Channel.class))).thenAnswer((invocation) -> {
            Channel channel = invocation.getArgument(0);
            channels.put(channel.getBroadcaster(), channel);
            return channel;
        });
        when(channelRepository.findAll()).thenAnswer((invocation) -> List.copyOf(channels.values()));
        when(channelRepository.findTop50ByBroadcasterContainingIgnoreCaseOrderByBroadcasterAsc(anyString())).thenAnswer(
            (invocation) ->
                channels
                    .values()
                    .stream()
                    .filter((channel) ->
                        Optional.ofNullable(channel.getBroadcaster())
                            .orElse("")
                            .contains(
                                Optional.ofNullable(invocation.getArgument(0, String.class)).orElse("").toLowerCase()
                            )
                    )
                    .sorted(Comparator.comparing(Channel::getBroadcaster))
                    .limit(50)
                    .toList()
        );

        when(assetRepository.save(any(Asset.class))).thenAnswer((invocation) -> {
            Asset asset = invocation.getArgument(0);
            assets.put(asset.getId(), asset);
            return asset;
        });
        when(assetRepository.findById(anyString())).thenAnswer((invocation) ->
            Optional.ofNullable(assets.get(invocation.getArgument(0)))
        );
        when(assetRepository.findByBroadcaster(anyString())).thenAnswer((invocation) ->
            filterAssetsByBroadcaster(assets.values(), invocation.getArgument(0))
        );
        doAnswer((invocation) -> assets.remove(invocation.getArgument(0, Asset.class).getId()))
            .when(assetRepository)
            .delete(any(Asset.class));

        when(visualAssetRepository.save(any(dev.kruhlmann.imgfloat.model.VisualAsset.class))).thenAnswer(
            (invocation) -> {
                dev.kruhlmann.imgfloat.model.VisualAsset visual = invocation.getArgument(0);
                visualAssets.put(visual.getId(), visual);
                return visual;
            }
        );
        when(visualAssetRepository.findById(anyString())).thenAnswer((invocation) ->
            Optional.ofNullable(visualAssets.get(invocation.getArgument(0)))
        );
        when(visualAssetRepository.findByIdIn(any())).thenAnswer((invocation) -> {
            Collection<String> ids = invocation.getArgument(0);
            return visualAssets
                .values()
                .stream()
                .filter((visual) -> ids.contains(visual.getId()))
                .toList();
        });
        when(visualAssetRepository.findByIdInAndHiddenFalse(any())).thenAnswer((invocation) -> {
            Collection<String> ids = invocation.getArgument(0);
            return visualAssets
                .values()
                .stream()
                .filter((visual) -> ids.contains(visual.getId()))
                .filter((visual) -> !visual.isHidden())
                .toList();
        });
        doAnswer((invocation) -> visualAssets.remove(invocation.getArgument(0, String.class)))
            .when(visualAssetRepository)
            .deleteById(anyString());

        when(audioAssetRepository.save(any(AudioAsset.class))).thenAnswer((invocation) -> {
            AudioAsset audio = invocation.getArgument(0);
            audioAssets.put(audio.getId(), audio);
            return audio;
        });
        when(audioAssetRepository.findById(anyString())).thenAnswer((invocation) ->
            Optional.ofNullable(audioAssets.get(invocation.getArgument(0)))
        );
        when(audioAssetRepository.findByIdIn(any())).thenAnswer((invocation) -> {
            Collection<String> ids = invocation.getArgument(0);
            return audioAssets
                .values()
                .stream()
                .filter((audio) -> ids.contains(audio.getId()))
                .toList();
        });
        doAnswer((invocation) -> audioAssets.remove(invocation.getArgument(0, String.class)))
            .when(audioAssetRepository)
            .deleteById(anyString());

        when(scriptAssetRepository.save(any(ScriptAsset.class))).thenAnswer((invocation) -> {
            ScriptAsset script = invocation.getArgument(0);
            scriptAssets.put(script.getId(), script);
            return script;
        });
        when(scriptAssetRepository.findById(anyString())).thenAnswer((invocation) ->
            Optional.ofNullable(scriptAssets.get(invocation.getArgument(0)))
        );
        when(scriptAssetRepository.findByIdIn(any())).thenAnswer((invocation) -> {
            Collection<String> ids = invocation.getArgument(0);
            return scriptAssets
                .values()
                .stream()
                .filter((script) -> ids.contains(script.getId()))
                .toList();
        });
        doAnswer((invocation) -> scriptAssets.remove(invocation.getArgument(0, String.class)))
            .when(scriptAssetRepository)
            .deleteById(anyString());
    }

    private List<Asset> filterAssetsByBroadcaster(Collection<Asset> assets, String broadcaster) {
        return assets
            .stream()
            .filter((asset) -> asset.getBroadcaster().equalsIgnoreCase(broadcaster))
            .toList();
    }
}
