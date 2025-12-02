package com.imgfloat.app;

import com.imgfloat.app.model.AssetRequest;
import com.imgfloat.app.model.TransformRequest;
import com.imgfloat.app.model.VisibilityRequest;
import com.imgfloat.app.service.ChannelDirectoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ChannelDirectoryServiceTest {
    private ChannelDirectoryService service;
    private SimpMessagingTemplate messagingTemplate;

    @BeforeEach
    void setup() {
        messagingTemplate = mock(SimpMessagingTemplate.class);
        service = new ChannelDirectoryService(messagingTemplate);
    }

    @Test
    void createsAssetsAndBroadcastsEvents() {
        AssetRequest request = new AssetRequest();
        request.setUrl("https://example.com/image.png");
        request.setWidth(1200);
        request.setHeight(800);

        Optional<?> created = service.createAsset("caster", request);
        assertThat(created).isPresent();
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(org.mockito.ArgumentMatchers.contains("/topic/channel/caster"), captor.capture());
    }

    @Test
    void updatesTransformAndVisibility() {
        AssetRequest request = new AssetRequest();
        request.setUrl("https://example.com/image.png");
        request.setWidth(400);
        request.setHeight(300);

        String channel = "caster";
        String id = service.createAsset(channel, request).orElseThrow().getId();

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
}
