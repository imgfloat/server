package dev.kruhlmann.imgfloat.service;

import dev.kruhlmann.imgfloat.model.Asset;
import dev.kruhlmann.imgfloat.model.AssetEvent;
import dev.kruhlmann.imgfloat.model.AssetPatch;
import dev.kruhlmann.imgfloat.model.Channel;
import dev.kruhlmann.imgfloat.model.AssetView;
import dev.kruhlmann.imgfloat.model.CanvasSettingsRequest;
import dev.kruhlmann.imgfloat.model.PlaybackRequest;
import dev.kruhlmann.imgfloat.model.TransformRequest;
import dev.kruhlmann.imgfloat.model.VisibilityRequest;
import dev.kruhlmann.imgfloat.repository.AssetRepository;
import dev.kruhlmann.imgfloat.repository.ChannelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import dev.kruhlmann.imgfloat.service.media.AssetContent;
import dev.kruhlmann.imgfloat.service.media.MediaDetectionService;
import dev.kruhlmann.imgfloat.service.media.MediaOptimizationService;
import dev.kruhlmann.imgfloat.service.media.OptimizedAsset;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class ChannelDirectoryService {
    private static final double MAX_SPEED = 4.0;
    private static final double MIN_AUDIO_SPEED = 0.1;
    private static final double MAX_AUDIO_SPEED = 4.0;
    private static final double MIN_AUDIO_PITCH = 0.5;
    private static final double MAX_AUDIO_PITCH = 2.0;
    private static final double MAX_AUDIO_VOLUME = 1.0;
    private static final Logger logger = LoggerFactory.getLogger(ChannelDirectoryService.class);
    private final ChannelRepository channelRepository;
    private final AssetRepository assetRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final AssetStorageService assetStorageService;
    private final MediaDetectionService mediaDetectionService;
    private final MediaOptimizationService mediaOptimizationService;

    public ChannelDirectoryService(ChannelRepository channelRepository,
                                   AssetRepository assetRepository,
                                   SimpMessagingTemplate messagingTemplate,
                                   AssetStorageService assetStorageService,
                                   MediaDetectionService mediaDetectionService,
                                   MediaOptimizationService mediaOptimizationService) {
        this.channelRepository = channelRepository;
        this.assetRepository = assetRepository;
        this.messagingTemplate = messagingTemplate;
        this.assetStorageService = assetStorageService;
        this.mediaDetectionService = mediaDetectionService;
        this.mediaOptimizationService = mediaOptimizationService;
    }

    public Channel getOrCreateChannel(String broadcaster) {
        String normalized = normalize(broadcaster);
        return channelRepository.findById(normalized)
                .orElseGet(() -> channelRepository.save(new Channel(normalized)));
    }

    public List<String> searchBroadcasters(String query) {
        String normalizedQuery = normalize(query);
        String searchTerm = normalizedQuery == null || normalizedQuery.isBlank() ? "" : normalizedQuery;
        return channelRepository.findTop50ByBroadcasterContainingIgnoreCaseOrderByBroadcasterAsc(searchTerm)
                .stream()
                .map(Channel::getBroadcaster)
                .toList();
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

    public Collection<AssetView> getAssetsForAdmin(String broadcaster) {
        String normalized = normalize(broadcaster);
        return sortAndMapAssets(normalized, assetRepository.findByBroadcaster(normalized));
    }

    public Collection<AssetView> getVisibleAssets(String broadcaster) {
        String normalized = normalize(broadcaster);
        return sortAndMapAssets(normalized, assetRepository.findByBroadcasterAndHiddenFalse(normalize(broadcaster)));
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

    public Optional<AssetView> createAsset(String broadcaster, MultipartFile file) throws IOException {
        Channel channel = getOrCreateChannel(broadcaster);
        byte[] bytes = file.getBytes();
        String mediaType = mediaDetectionService.detectMediaType(file, bytes);

        OptimizedAsset optimized = mediaOptimizationService.optimizeAsset(bytes, mediaType);
        if (optimized == null) {
            return Optional.empty();
        }

        String name = Optional.ofNullable(file.getOriginalFilename())
                .map(filename -> filename.replaceAll("^.*[/\\\\]", ""))
                .filter(s -> !s.isBlank())
                .orElse("Asset " + System.currentTimeMillis());

        double width = optimized.width() > 0 ? optimized.width() : (optimized.mediaType().startsWith("audio/") ? 400 : 640);
        double height = optimized.height() > 0 ? optimized.height() : (optimized.mediaType().startsWith("audio/") ? 80 : 360);
        Asset asset = new Asset(channel.getBroadcaster(), name, "", width, height);
        asset.setOriginalMediaType(mediaType);
        asset.setMediaType(optimized.mediaType());
        asset.setUrl(assetStorageService.storeAsset(channel.getBroadcaster(), asset.getId(), optimized.bytes(), optimized.mediaType()));
        asset.setPreview(assetStorageService.storePreview(channel.getBroadcaster(), asset.getId(), optimized.previewBytes()));
        asset.setSpeed(1.0);
        asset.setMuted(optimized.mediaType().startsWith("video/"));
        asset.setAudioLoop(false);
        asset.setAudioDelayMillis(0);
        asset.setAudioSpeed(1.0);
        asset.setAudioPitch(1.0);
        asset.setAudioVolume(1.0);
        asset.setZIndex(nextZIndex(channel.getBroadcaster()));

        assetRepository.save(asset);
        AssetView view = AssetView.from(channel.getBroadcaster(), asset);
        messagingTemplate.convertAndSend(topicFor(broadcaster), AssetEvent.created(broadcaster, view));
        return Optional.of(view);
    }

    public Optional<AssetView> updateTransform(String broadcaster, String assetId, TransformRequest request) {
        String normalized = normalize(broadcaster);
        return assetRepository.findById(assetId)
                .filter(asset -> normalized.equals(asset.getBroadcaster()))
                .map(asset -> {
                    validateTransform(request);
                    asset.setX(request.getX());
                    asset.setY(request.getY());
                    asset.setWidth(request.getWidth());
                    asset.setHeight(request.getHeight());
                    asset.setRotation(request.getRotation());
                    if (request.getZIndex() != null) {
                        asset.setZIndex(request.getZIndex());
                    }
                    if (request.getSpeed() != null) {
                        asset.setSpeed(request.getSpeed());
                    }
                    if (request.getMuted() != null && asset.isVideo()) {
                        asset.setMuted(request.getMuted());
                    }
                    if (request.getAudioLoop() != null) {
                        asset.setAudioLoop(request.getAudioLoop());
                    }
                    if (request.getAudioDelayMillis() != null) {
                        asset.setAudioDelayMillis(request.getAudioDelayMillis());
                    }
                    if (request.getAudioSpeed() != null) {
                        asset.setAudioSpeed(request.getAudioSpeed());
                    }
                    if (request.getAudioPitch() != null) {
                        asset.setAudioPitch(request.getAudioPitch());
                    }
                    if (request.getAudioVolume() != null) {
                        asset.setAudioVolume(request.getAudioVolume());
                    }
                    assetRepository.save(asset);
                    AssetView view = AssetView.from(normalized, asset);
                    AssetPatch patch = AssetPatch.fromTransform(asset);
                    messagingTemplate.convertAndSend(topicFor(broadcaster), AssetEvent.updated(broadcaster, patch));
                    return view;
                });
    }

    private void validateTransform(TransformRequest request) {
        if (request.getWidth() <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Width must be greater than 0");
        }
        if (request.getHeight() <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Height must be greater than 0");
        }
        if (request.getSpeed() != null && (request.getSpeed() < 0 || request.getSpeed() > MAX_SPEED)) {
            throw new ResponseStatusException(BAD_REQUEST, "Playback speed must be between 0 and " + MAX_SPEED);
        }
        if (request.getZIndex() != null && request.getZIndex() < 1) {
            throw new ResponseStatusException(BAD_REQUEST, "zIndex must be at least 1");
        }
        if (request.getAudioDelayMillis() != null && request.getAudioDelayMillis() < 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Audio delay must be zero or greater");
        }
        if (request.getAudioSpeed() != null && (request.getAudioSpeed() < MIN_AUDIO_SPEED || request.getAudioSpeed() > MAX_AUDIO_SPEED)) {
            throw new ResponseStatusException(BAD_REQUEST, "Audio speed must be between " + MIN_AUDIO_SPEED + " and " + MAX_AUDIO_SPEED + "x");
        }
        if (request.getAudioPitch() != null && (request.getAudioPitch() < MIN_AUDIO_PITCH || request.getAudioPitch() > MAX_AUDIO_PITCH)) {
            throw new ResponseStatusException(BAD_REQUEST, "Audio pitch must be between " + MIN_AUDIO_PITCH + " and " + MAX_AUDIO_PITCH + "x");
        }
        if (request.getAudioVolume() != null && (request.getAudioVolume() < 0 || request.getAudioVolume() > MAX_AUDIO_VOLUME)) {
            throw new ResponseStatusException(BAD_REQUEST, "Audio volume must be between 0 and " + MAX_AUDIO_VOLUME);
        }
    }

    public Optional<AssetView> triggerPlayback(String broadcaster, String assetId, PlaybackRequest request) {
        String normalized = normalize(broadcaster);
        return assetRepository.findById(assetId)
                .filter(asset -> normalized.equals(asset.getBroadcaster()))
                .map(asset -> {
                    AssetView view = AssetView.from(normalized, asset);
                    boolean shouldPlay = request == null || request.getPlay();
                    messagingTemplate.convertAndSend(topicFor(broadcaster), AssetEvent.play(broadcaster, view, shouldPlay));
                    return view;
                });
    }

    public Optional<AssetView> updateVisibility(String broadcaster, String assetId, VisibilityRequest request) {
        String normalized = normalize(broadcaster);
        return assetRepository.findById(assetId)
                .filter(asset -> normalized.equals(asset.getBroadcaster()))
                .map(asset -> {
                    asset.setHidden(request.isHidden());
                    assetRepository.save(asset);
                    AssetView view = AssetView.from(normalized, asset);
                    AssetPatch patch = AssetPatch.fromVisibility(asset);
                    messagingTemplate.convertAndSend(topicFor(broadcaster), AssetEvent.visibility(broadcaster, patch));
                    return view;
                });
    }

    public boolean deleteAsset(String broadcaster, String assetId) {
        String normalized = normalize(broadcaster);
        return assetRepository.findById(assetId)
                .filter(asset -> normalized.equals(asset.getBroadcaster()))
                .map(asset -> {
                    assetStorageService.deleteAssetFile(asset.getUrl());
                    assetStorageService.deletePreviewFile(asset.getPreview());
                    assetRepository.delete(asset);
                    messagingTemplate.convertAndSend(topicFor(broadcaster), AssetEvent.deleted(broadcaster, assetId));
                    return true;
                })
                .orElse(false);
    }

    public Optional<AssetContent> getAssetContent(String broadcaster, String assetId) {
        String normalized = normalize(broadcaster);
        return assetRepository.findById(assetId)
                .filter(asset -> normalized.equals(asset.getBroadcaster()))
                .flatMap(this::decodeAssetData);
    }

    public Optional<AssetContent> getVisibleAssetContent(String broadcaster, String assetId) {
        String normalized = normalize(broadcaster);
        return assetRepository.findById(assetId)
                .filter(asset -> normalized.equals(asset.getBroadcaster()))
                .filter(asset -> !asset.isHidden())
                .flatMap(this::decodeAssetData);
    }

    public Optional<AssetContent> getAssetPreview(String broadcaster, String assetId, boolean includeHidden) {
        String normalized = normalize(broadcaster);
        return assetRepository.findById(assetId)
                .filter(asset -> normalized.equals(asset.getBroadcaster()))
                .filter(asset -> includeHidden || !asset.isHidden())
                .map(asset -> {
                    Optional<AssetContent> preview = assetStorageService.loadPreview(asset.getPreview())
                            .or(() -> decodeDataUrl(asset.getPreview()));
                    if (preview.isPresent()) {
                        return preview.get();
                    }
                    if (asset.getMediaType() != null && asset.getMediaType().startsWith("image/")) {
                        return decodeAssetData(asset).orElse(null);
                    }
                    return null;
                })
                .flatMap(Optional::ofNullable);
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
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private List<AssetView> sortAndMapAssets(String broadcaster, Collection<Asset> assets) {
        return assets.stream()
                .sorted(Comparator.comparingInt(Asset::getZIndex)
                        .thenComparing(Asset::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(asset -> AssetView.from(broadcaster, asset))
                .toList();
    }

    private Optional<AssetContent> decodeAssetData(Asset asset) {
        return assetStorageService.loadAssetFile(asset.getUrl(), asset.getMediaType())
                .or(() -> decodeDataUrl(asset.getUrl()))
                .or(() -> {
                    logger.warn("Unable to decode asset data for {}", asset.getId());
                    return Optional.empty();
                });
    }

    private Optional<AssetContent> decodeDataUrl(String dataUrl) {
        if (dataUrl == null || !dataUrl.startsWith("data:")) {
            return Optional.empty();
        }
        int commaIndex = dataUrl.indexOf(',');
        if (commaIndex < 0) {
            return Optional.empty();
        }
        String metadata = dataUrl.substring(5, commaIndex);
        String[] parts = metadata.split(";", 2);
        String mediaType = parts.length > 0 && !parts[0].isBlank() ? parts[0] : "application/octet-stream";
        String encoded = dataUrl.substring(commaIndex + 1);
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            return Optional.of(new AssetContent(bytes, mediaType));
        } catch (IllegalArgumentException e) {
            logger.warn("Unable to decode data url", e);
            return Optional.empty();
        }
    }

    private int nextZIndex(String broadcaster) {
        return assetRepository.findByBroadcaster(normalize(broadcaster)).stream()
                .mapToInt(Asset::getZIndex)
                .max()
                .orElse(0) + 1;
    }

}
