package com.imgfloat.app.service;

import com.imgfloat.app.model.Asset;
import com.imgfloat.app.model.AssetEvent;
import com.imgfloat.app.model.Channel;
import com.imgfloat.app.model.CanvasSettingsRequest;
import com.imgfloat.app.model.TransformRequest;
import com.imgfloat.app.model.VisibilityRequest;
import com.imgfloat.app.repository.AssetRepository;
import com.imgfloat.app.repository.ChannelRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;

@Service
public class ChannelDirectoryService {
    private final ChannelRepository channelRepository;
    private final AssetRepository assetRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ChannelDirectoryService(ChannelRepository channelRepository,
                                   AssetRepository assetRepository,
                                   SimpMessagingTemplate messagingTemplate) {
        this.channelRepository = channelRepository;
        this.assetRepository = assetRepository;
        this.messagingTemplate = messagingTemplate;
    }

    public Channel getOrCreateChannel(String broadcaster) {
        String normalized = normalize(broadcaster);
        return channelRepository.findById(normalized)
                .orElseGet(() -> channelRepository.save(new Channel(normalized)));
    }

    public boolean addAdmin(String broadcaster, String username) {
        Channel channel = getOrCreateChannel(broadcaster);
        boolean added = channel.addAdmin(username);
        if (added) {
            channelRepository.save(channel);
            messagingTemplate.convertAndSend(topicFor(broadcaster), "Admin added: " + username);
        }
        return added;
    }

    public boolean removeAdmin(String broadcaster, String username) {
        Channel channel = getOrCreateChannel(broadcaster);
        boolean removed = channel.removeAdmin(username);
        if (removed) {
            channelRepository.save(channel);
            messagingTemplate.convertAndSend(topicFor(broadcaster), "Admin removed: " + username);
        }
        return removed;
    }

    public Collection<Asset> getAssetsForAdmin(String broadcaster) {
        return assetRepository.findByBroadcaster(normalize(broadcaster));
    }

    public Collection<Asset> getVisibleAssets(String broadcaster) {
        return assetRepository.findByBroadcasterAndHiddenFalse(normalize(broadcaster));
    }

    public CanvasSettingsRequest getCanvasSettings(String broadcaster) {
        Channel channel = getOrCreateChannel(broadcaster);
        return new CanvasSettingsRequest(channel.getCanvasWidth(), channel.getCanvasHeight());
    }

    public CanvasSettingsRequest updateCanvasSettings(String broadcaster, CanvasSettingsRequest request) {
        Channel channel = getOrCreateChannel(broadcaster);
        channel.setCanvasWidth(request.getWidth());
        channel.setCanvasHeight(request.getHeight());
        channelRepository.save(channel);
        return new CanvasSettingsRequest(channel.getCanvasWidth(), channel.getCanvasHeight());
    }

    public Optional<Asset> createAsset(String broadcaster, MultipartFile file) throws IOException {
        Channel channel = getOrCreateChannel(broadcaster);
        byte[] bytes = file.getBytes();
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) {
            return Optional.empty();
        }
        String name = Optional.ofNullable(file.getOriginalFilename())
                .map(filename -> filename.replaceAll("^.*[/\\\\]", ""))
                .filter(s -> !s.isBlank())
                .orElse("Asset " + System.currentTimeMillis());
        String contentType = Optional.ofNullable(file.getContentType()).orElse("application/octet-stream");
        String dataUrl = "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(bytes);
        Asset asset = new Asset(channel.getBroadcaster(), name, dataUrl, image.getWidth(), image.getHeight());
        assetRepository.save(asset);
        messagingTemplate.convertAndSend(topicFor(broadcaster), AssetEvent.created(broadcaster, asset));
        return Optional.of(asset);
    }

    public Optional<Asset> updateTransform(String broadcaster, String assetId, TransformRequest request) {
        String normalized = normalize(broadcaster);
        return assetRepository.findById(assetId)
                .filter(asset -> normalized.equals(asset.getBroadcaster()))
                .map(asset -> {
                    asset.setX(request.getX());
                    asset.setY(request.getY());
                    asset.setWidth(request.getWidth());
                    asset.setHeight(request.getHeight());
                    asset.setRotation(request.getRotation());
                    assetRepository.save(asset);
                    messagingTemplate.convertAndSend(topicFor(broadcaster), AssetEvent.updated(broadcaster, asset));
                    return asset;
                });
    }

    public Optional<Asset> updateVisibility(String broadcaster, String assetId, VisibilityRequest request) {
        String normalized = normalize(broadcaster);
        return assetRepository.findById(assetId)
                .filter(asset -> normalized.equals(asset.getBroadcaster()))
                .map(asset -> {
                    asset.setHidden(request.isHidden());
                    assetRepository.save(asset);
                    messagingTemplate.convertAndSend(topicFor(broadcaster), AssetEvent.visibility(broadcaster, asset));
                    return asset;
                });
    }

    public boolean deleteAsset(String broadcaster, String assetId) {
        String normalized = normalize(broadcaster);
        return assetRepository.findById(assetId)
                .filter(asset -> normalized.equals(asset.getBroadcaster()))
                .map(asset -> {
                    assetRepository.delete(asset);
                    messagingTemplate.convertAndSend(topicFor(broadcaster), AssetEvent.deleted(broadcaster, assetId));
                    return true;
                })
                .orElse(false);
    }

    public boolean isBroadcaster(String broadcaster, String username) {
        return broadcaster != null && broadcaster.equalsIgnoreCase(username);
    }

    public boolean isAdmin(String broadcaster, String username) {
        return channelRepository.findById(normalize(broadcaster))
                .map(Channel::getAdmins)
                .map(admins -> admins.contains(normalize(username)))
                .orElse(false);
    }

    public Collection<String> adminChannelsFor(String username) {
        if (username == null) {
            return List.of();
        }
        String login = username.toLowerCase();
        return channelRepository.findAll().stream()
                .filter(channel -> channel.getAdmins().contains(login))
                .map(Channel::getBroadcaster)
                .toList();
    }

    private String topicFor(String broadcaster) {
        return "/topic/channel/" + broadcaster.toLowerCase();
    }

    private String normalize(String value) {
        return value == null ? null : value.toLowerCase();
    }
}
