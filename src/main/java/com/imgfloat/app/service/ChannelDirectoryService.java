package com.imgfloat.app.service;

import com.imgfloat.app.model.Asset;
import com.imgfloat.app.model.AssetEvent;
import com.imgfloat.app.model.AssetRequest;
import com.imgfloat.app.model.Channel;
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

    public Collection<Asset> getAssetsForAdmin(String broadcaster) {
        return getOrCreateChannel(broadcaster).getAssets().values();
    }

    public Collection<Asset> getVisibleAssets(String broadcaster) {
        return getOrCreateChannel(broadcaster).getAssets().values().stream()
                .filter(asset -> !asset.isHidden())
                .toList();
    }

    public Optional<Asset> createAsset(String broadcaster, AssetRequest request) {
        Channel channel = getOrCreateChannel(broadcaster);
        Asset asset = new Asset(request.getUrl(), request.getWidth(), request.getHeight());
        channel.getAssets().put(asset.getId(), asset);
        messagingTemplate.convertAndSend(topicFor(broadcaster), AssetEvent.created(broadcaster, asset));
        return Optional.of(asset);
    }

    public Optional<Asset> updateTransform(String broadcaster, String assetId, TransformRequest request) {
        Channel channel = getOrCreateChannel(broadcaster);
        Asset asset = channel.getAssets().get(assetId);
        if (asset == null) {
            return Optional.empty();
        }
        asset.setX(request.getX());
        asset.setY(request.getY());
        asset.setWidth(request.getWidth());
        asset.setHeight(request.getHeight());
        asset.setRotation(request.getRotation());
        messagingTemplate.convertAndSend(topicFor(broadcaster), AssetEvent.updated(broadcaster, asset));
        return Optional.of(asset);
    }

    public Optional<Asset> updateVisibility(String broadcaster, String assetId, VisibilityRequest request) {
        Channel channel = getOrCreateChannel(broadcaster);
        Asset asset = channel.getAssets().get(assetId);
        if (asset == null) {
            return Optional.empty();
        }
        asset.setHidden(request.isHidden());
        messagingTemplate.convertAndSend(topicFor(broadcaster), AssetEvent.visibility(broadcaster, asset));
        return Optional.of(asset);
    }

    public boolean deleteAsset(String broadcaster, String assetId) {
        Channel channel = getOrCreateChannel(broadcaster);
        Asset removed = channel.getAssets().remove(assetId);
        if (removed != null) {
            messagingTemplate.convertAndSend(topicFor(broadcaster), AssetEvent.deleted(broadcaster, assetId));
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
