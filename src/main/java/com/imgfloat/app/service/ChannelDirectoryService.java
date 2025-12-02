package com.imgfloat.app.service;

import com.imgfloat.app.model.Channel;
import com.imgfloat.app.model.ImageEvent;
import com.imgfloat.app.model.ImageLayer;
import com.imgfloat.app.model.ImageRequest;
import com.imgfloat.app.model.TransformRequest;
import com.imgfloat.app.model.VisibilityRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChannelDirectoryService {
    private final Map<String, Channel> channels = new ConcurrentHashMap<>();
    private final SimpMessagingTemplate messagingTemplate;

    public ChannelDirectoryService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public Channel getOrCreateChannel(String broadcaster) {
        return channels.computeIfAbsent(broadcaster.toLowerCase(), Channel::new);
    }

    public boolean addAdmin(String broadcaster, String username) {
        Channel channel = getOrCreateChannel(broadcaster);
        boolean added = channel.addAdmin(username);
        if (added) {
            messagingTemplate.convertAndSend(topicFor(broadcaster), "Admin added: " + username);
        }
        return added;
    }

    public boolean removeAdmin(String broadcaster, String username) {
        Channel channel = getOrCreateChannel(broadcaster);
        boolean removed = channel.removeAdmin(username);
        if (removed) {
            messagingTemplate.convertAndSend(topicFor(broadcaster), "Admin removed: " + username);
        }
        return removed;
    }

    public Collection<ImageLayer> getImagesForAdmin(String broadcaster) {
        return getOrCreateChannel(broadcaster).getImages().values();
    }

    public Collection<ImageLayer> getVisibleImages(String broadcaster) {
        return getOrCreateChannel(broadcaster).getImages().values().stream()
                .filter(image -> !image.isHidden())
                .toList();
    }

    public Optional<ImageLayer> createImage(String broadcaster, ImageRequest request) {
        Channel channel = getOrCreateChannel(broadcaster);
        ImageLayer layer = new ImageLayer(request.getUrl(), request.getWidth(), request.getHeight());
        channel.getImages().put(layer.getId(), layer);
        messagingTemplate.convertAndSend(topicFor(broadcaster), ImageEvent.created(broadcaster, layer));
        return Optional.of(layer);
    }

    public Optional<ImageLayer> updateTransform(String broadcaster, String imageId, TransformRequest request) {
        Channel channel = getOrCreateChannel(broadcaster);
        ImageLayer layer = channel.getImages().get(imageId);
        if (layer == null) {
            return Optional.empty();
        }
        layer.setX(request.getX());
        layer.setY(request.getY());
        layer.setWidth(request.getWidth());
        layer.setHeight(request.getHeight());
        layer.setRotation(request.getRotation());
        messagingTemplate.convertAndSend(topicFor(broadcaster), ImageEvent.updated(broadcaster, layer));
        return Optional.of(layer);
    }

    public Optional<ImageLayer> updateVisibility(String broadcaster, String imageId, VisibilityRequest request) {
        Channel channel = getOrCreateChannel(broadcaster);
        ImageLayer layer = channel.getImages().get(imageId);
        if (layer == null) {
            return Optional.empty();
        }
        layer.setHidden(request.isHidden());
        messagingTemplate.convertAndSend(topicFor(broadcaster), ImageEvent.visibility(broadcaster, layer));
        return Optional.of(layer);
    }

    public boolean deleteImage(String broadcaster, String imageId) {
        Channel channel = getOrCreateChannel(broadcaster);
        ImageLayer removed = channel.getImages().remove(imageId);
        if (removed != null) {
            messagingTemplate.convertAndSend(topicFor(broadcaster), ImageEvent.deleted(broadcaster, imageId));
            return true;
        }
        return false;
    }

    public boolean isBroadcaster(String broadcaster, String username) {
        return broadcaster != null && broadcaster.equalsIgnoreCase(username);
    }

    public boolean isAdmin(String broadcaster, String username) {
        Channel channel = channels.get(broadcaster.toLowerCase());
        return channel != null && channel.getAdmins().contains(username.toLowerCase());
    }

    private String topicFor(String broadcaster) {
        return "/topic/channel/" + broadcaster.toLowerCase();
    }
}
