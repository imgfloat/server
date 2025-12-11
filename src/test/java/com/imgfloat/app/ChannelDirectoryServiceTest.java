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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

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

    @BeforeEach
    void setup() {
        messagingTemplate = mock(SimpMessagingTemplate.class);
        channelRepository = mock(ChannelRepository.class);
        assetRepository = mock(AssetRepository.class);
        setupInMemoryPersistence();
        service = new ChannelDirectoryService(channelRepository, assetRepository, messagingTemplate);
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
