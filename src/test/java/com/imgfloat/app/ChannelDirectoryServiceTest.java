package com.imgfloat.app;

import com.imgfloat.app.model.TransformRequest;
import com.imgfloat.app.model.VisibilityRequest;
import com.imgfloat.app.model.Asset;
import com.imgfloat.app.model.AssetView;
import com.imgfloat.app.model.Channel;
import com.imgfloat.app.repository.AssetRepository;
import com.imgfloat.app.repository.ChannelRepository;
import com.imgfloat.app.service.ChannelDirectoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;
import org.jcodec.api.awt.AWTSequenceEncoder;

import static org.assertj.core.api.Assertions.assertThat;
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
    private RecordingTaskExecutor taskExecutor;
    private Map<String, Channel> channels;
    private Map<String, Asset> assets;

    @BeforeEach
    void setup() {
        messagingTemplate = mock(SimpMessagingTemplate.class);
        channelRepository = mock(ChannelRepository.class);
        assetRepository = mock(AssetRepository.class);
        taskExecutor = new RecordingTaskExecutor();
        setupInMemoryPersistence();
        service = new ChannelDirectoryService(channelRepository, assetRepository, messagingTemplate, taskExecutor);
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
    void asyncGifOptimizationSwitchesToVideo() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "image.gif", "image/gif", largeGif());

        long start = System.currentTimeMillis();
        AssetView created = service.createAsset("caster", file).orElseThrow();
        long duration = System.currentTimeMillis() - start;

        assertThat(duration).isLessThan(750L);
        Asset placeholder = assets.get(created.id());
        assertThat(placeholder.getMediaType()).isEqualTo("image/gif");
        assertThat(placeholder.getPreview()).isNull();

        taskExecutor.runAll();

        Asset optimized = assets.get(created.id());
        assertThat(optimized.getMediaType()).isEqualTo("video/mp4");
        assertThat(optimized.getPreview()).isNotBlank();
    }

    @Test
    void videoPreviewGeneratedInBackground() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "clip.mp4", "video/mp4", sampleVideo());

        AssetView created = service.createAsset("caster", file).orElseThrow();
        Asset placeholder = assets.get(created.id());
        assertThat(placeholder.getPreview()).isNull();

        taskExecutor.runAll();

        Asset optimized = assets.get(created.id());
        assertThat(optimized.getPreview()).isNotBlank();
        assertThat(optimized.getMediaType()).isEqualTo("video/mp4");
    }

    @Test
    void updatesTransformAndVisibility() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", samplePng());
        String channel = "caster";
        String id = service.createAsset(channel, file).orElseThrow().id();

        TransformRequest transform = new TransformRequest();
        transform.setX(10);
        transform.setY(20);
        transform.setWidth(200);
        transform.setHeight(150);
        transform.setRotation(45);

        assertThat(service.updateTransform(channel, id, transform)).isPresent();

        VisibilityRequest visibilityRequest = new VisibilityRequest();
        visibilityRequest.setHidden(false);
        assertThat(service.updateVisibility(channel, id, visibilityRequest)).isPresent();
    }

    private byte[] samplePng() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private byte[] largeGif() throws IOException {
        BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "gif", out);
            return out.toByteArray();
        }
    }

    private byte[] sampleVideo() throws IOException {
        File temp = File.createTempFile("sample", ".mp4");
        temp.deleteOnExit();
        try {
            AWTSequenceEncoder encoder = AWTSequenceEncoder.createSequenceEncoder(temp, 10);
            for (int i = 0; i < 15; i++) {
                BufferedImage frame = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
                encoder.encodeImage(frame);
            }
            encoder.finish();
            return java.nio.file.Files.readAllBytes(temp.toPath());
        } finally {
            temp.delete();
        }
    }

    private void setupInMemoryPersistence() {
        channels = new ConcurrentHashMap<>();
        assets = new ConcurrentHashMap<>();

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

    private static class RecordingTaskExecutor implements TaskExecutor {
        private final List<Runnable> queued = new CopyOnWriteArrayList<>();

        @Override
        public void execute(Runnable task) {
            queued.add(task);
        }

        void runAll() {
            new ArrayList<>(queued).forEach(Runnable::run);
            queued.clear();
        }
    }
}
