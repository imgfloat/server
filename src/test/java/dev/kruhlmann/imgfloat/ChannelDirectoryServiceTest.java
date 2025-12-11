package dev.kruhlmann.imgfloat;

import dev.kruhlmann.imgfloat.model.TransformRequest;
import dev.kruhlmann.imgfloat.model.VisibilityRequest;
import dev.kruhlmann.imgfloat.model.Asset;
import dev.kruhlmann.imgfloat.model.AssetView;
import dev.kruhlmann.imgfloat.model.Channel;
import dev.kruhlmann.imgfloat.repository.AssetRepository;
import dev.kruhlmann.imgfloat.repository.ChannelRepository;
import dev.kruhlmann.imgfloat.service.ChannelDirectoryService;
import dev.kruhlmann.imgfloat.service.AssetStorageService;
import dev.kruhlmann.imgfloat.service.media.MediaDetectionService;
import dev.kruhlmann.imgfloat.service.media.MediaOptimizationService;
import dev.kruhlmann.imgfloat.service.media.MediaPreviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class ChannelDirectoryServiceTest {
    private ChannelDirectoryService service;
    private SimpMessagingTemplate messagingTemplate;
    private ChannelRepository channelRepository;
    private AssetRepository assetRepository;

    @BeforeEach
    void setup() throws Exception {
        messagingTemplate = mock(SimpMessagingTemplate.class);
        channelRepository = mock(ChannelRepository.class);
        assetRepository = mock(AssetRepository.class);
        setupInMemoryPersistence();
        Path assetRoot = Files.createTempDirectory("imgfloat-assets-test");
        Path previewRoot = Files.createTempDirectory("imgfloat-previews-test");
        AssetStorageService assetStorageService = new AssetStorageService(assetRoot.toString(), previewRoot.toString());
        MediaPreviewService mediaPreviewService = new MediaPreviewService();
        MediaOptimizationService mediaOptimizationService = new MediaOptimizationService(mediaPreviewService);
        MediaDetectionService mediaDetectionService = new MediaDetectionService();
        service = new ChannelDirectoryService(channelRepository, assetRepository, messagingTemplate,
                assetStorageService, mediaDetectionService, mediaOptimizationService, 26214400L);
    }

    @Test
    void createsAssetsAndBroadcastsEvents() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", samplePng());

        Optional<AssetView> created = service.createAsset("caster", file);
        assertThat(created).isPresent();
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(org.mockito.ArgumentMatchers.contains("/topic/channel/caster"), captor.capture());
    }

    @Test
    void updatesTransformAndVisibility() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", samplePng());
        String channel = "caster";
        String id = service.createAsset(channel, file).orElseThrow().id();

        TransformRequest transform = validTransform();

        assertThat(service.updateTransform(channel, id, transform)).isPresent();

        VisibilityRequest visibilityRequest = new VisibilityRequest();
        visibilityRequest.setHidden(false);
        assertThat(service.updateVisibility(channel, id, visibilityRequest)).isPresent();
    }

    @Test
    void rejectsInvalidTransformDimensions() throws Exception {
        String channel = "caster";
        String id = createSampleAsset(channel);

        TransformRequest transform = validTransform();
        transform.setWidth(0);

        assertThatThrownBy(() -> service.updateTransform(channel, id, transform))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Width must be greater than 0");
    }

    @Test
    void rejectsOutOfRangePlaybackValues() throws Exception {
        String channel = "caster";
        String id = createSampleAsset(channel);

        TransformRequest speedTransform = validTransform();
        speedTransform.setSpeed(5.0);

        assertThatThrownBy(() -> service.updateTransform(channel, id, speedTransform))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Playback speed must be between 0 and 4.0");

        TransformRequest volumeTransform = validTransform();
        volumeTransform.setAudioVolume(1.5);

        assertThatThrownBy(() -> service.updateTransform(channel, id, volumeTransform))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Audio volume must be between 0 and 1.0");
    }

    @Test
    void appliesBoundaryValues() throws Exception {
        String channel = "caster";
        String id = createSampleAsset(channel);

        TransformRequest transform = validTransform();
        transform.setSpeed(0.0);
        transform.setAudioSpeed(0.1);
        transform.setAudioPitch(0.5);
        transform.setAudioVolume(1.0);
        transform.setAudioDelayMillis(0);
        transform.setZIndex(1);

        AssetView view = service.updateTransform(channel, id, transform).orElseThrow();

        assertThat(view.speed()).isEqualTo(0.0);
        assertThat(view.audioSpeed()).isEqualTo(0.1);
        assertThat(view.audioPitch()).isEqualTo(0.5);
        assertThat(view.audioVolume()).isEqualTo(1.0);
        assertThat(view.audioDelayMillis()).isEqualTo(0);
        assertThat(view.zIndex()).isEqualTo(1);
    }

    private byte[] samplePng() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private String createSampleAsset(String channel) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", samplePng());
        return service.createAsset(channel, file).orElseThrow().id();
    }

    private TransformRequest validTransform() {
        TransformRequest transform = new TransformRequest();
        transform.setX(10);
        transform.setY(20);
        transform.setWidth(200);
        transform.setHeight(150);
        transform.setRotation(45);
        return transform;
    }

    private void setupInMemoryPersistence() {
        Map<String, Channel> channels = new ConcurrentHashMap<>();
        Map<String, Asset> assets = new ConcurrentHashMap<>();

        when(channelRepository.findById(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(channels.get(invocation.getArgument(0))));
        when(channelRepository.save(any(Channel.class)))
                .thenAnswer(invocation -> {
                    Channel channel = invocation.getArgument(0);
                    channels.put(channel.getBroadcaster(), channel);
                    return channel;
                });
        when(channelRepository.findAll())
                .thenAnswer(invocation -> List.copyOf(channels.values()));
        when(channelRepository.findTop50ByBroadcasterContainingIgnoreCaseOrderByBroadcasterAsc(anyString()))
                .thenAnswer(invocation -> channels.values().stream()
                        .filter(channel -> Optional.ofNullable(channel.getBroadcaster()).orElse("")
                                .contains(Optional.ofNullable(invocation.getArgument(0, String.class)).orElse("").toLowerCase()))
                        .sorted(Comparator.comparing(Channel::getBroadcaster))
                        .limit(50)
                        .toList());

        when(assetRepository.save(any(Asset.class)))
                .thenAnswer(invocation -> {
                    Asset asset = invocation.getArgument(0);
                    assets.put(asset.getId(), asset);
                    return asset;
                });
        when(assetRepository.findById(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(assets.get(invocation.getArgument(0))));
        when(assetRepository.findByBroadcaster(anyString()))
                .thenAnswer(invocation -> filterAssetsByBroadcaster(assets.values(), invocation.getArgument(0), false));
        when(assetRepository.findByBroadcasterAndHiddenFalse(anyString()))
                .thenAnswer(invocation -> filterAssetsByBroadcaster(assets.values(), invocation.getArgument(0), true));
        doAnswer(invocation -> assets.remove(invocation.getArgument(0, Asset.class).getId()))
                .when(assetRepository).delete(any(Asset.class));
    }

    private List<Asset> filterAssetsByBroadcaster(Collection<Asset> assets, String broadcaster, boolean onlyVisible) {
        return assets.stream()
                .filter(asset -> asset.getBroadcaster().equalsIgnoreCase(broadcaster))
                .filter(asset -> !onlyVisible || !asset.isHidden())
                .toList();
    }
}
