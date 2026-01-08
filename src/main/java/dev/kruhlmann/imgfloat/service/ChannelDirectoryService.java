package dev.kruhlmann.imgfloat.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE;

import dev.kruhlmann.imgfloat.model.Asset;
import dev.kruhlmann.imgfloat.model.AssetEvent;
import dev.kruhlmann.imgfloat.model.AssetPatch;
import dev.kruhlmann.imgfloat.model.AssetView;
import dev.kruhlmann.imgfloat.model.CanvasEvent;
import dev.kruhlmann.imgfloat.model.CanvasSettingsRequest;
import dev.kruhlmann.imgfloat.model.Channel;
import dev.kruhlmann.imgfloat.model.CodeAssetRequest;
import dev.kruhlmann.imgfloat.model.PlaybackRequest;
import dev.kruhlmann.imgfloat.model.Settings;
import dev.kruhlmann.imgfloat.model.TransformRequest;
import dev.kruhlmann.imgfloat.model.VisibilityRequest;
import dev.kruhlmann.imgfloat.repository.AssetRepository;
import dev.kruhlmann.imgfloat.repository.ChannelRepository;
import dev.kruhlmann.imgfloat.service.media.AssetContent;
import dev.kruhlmann.imgfloat.service.media.MediaDetectionService;
import dev.kruhlmann.imgfloat.service.media.MediaOptimizationService;
import dev.kruhlmann.imgfloat.service.media.OptimizedAsset;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChannelDirectoryService {

    private static final Logger logger = LoggerFactory.getLogger(ChannelDirectoryService.class);
    private static final Pattern SAFE_FILENAME = Pattern.compile("[^a-zA-Z0-9._ -]");
    private static final String DEFAULT_CODE_MEDIA_TYPE = "application/javascript";

    private final ChannelRepository channelRepository;
    private final AssetRepository assetRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final AssetStorageService assetStorageService;
    private final MediaDetectionService mediaDetectionService;
    private final MediaOptimizationService mediaOptimizationService;
    private final SettingsService settingsService;
    private final long uploadLimitBytes;

    @Autowired
    public ChannelDirectoryService(
        ChannelRepository channelRepository,
        AssetRepository assetRepository,
        SimpMessagingTemplate messagingTemplate,
        AssetStorageService assetStorageService,
        MediaDetectionService mediaDetectionService,
        MediaOptimizationService mediaOptimizationService,
        SettingsService settingsService,
        long uploadLimitBytes
    ) {
        this.channelRepository = channelRepository;
        this.assetRepository = assetRepository;
        this.messagingTemplate = messagingTemplate;
        this.assetStorageService = assetStorageService;
        this.mediaDetectionService = mediaDetectionService;
        this.mediaOptimizationService = mediaOptimizationService;
        this.settingsService = settingsService;
        this.uploadLimitBytes = uploadLimitBytes;
    }

    public Channel getOrCreateChannel(String broadcaster) {
        String normalized = normalize(broadcaster);
        return channelRepository.findById(normalized).orElseGet(() -> channelRepository.save(new Channel(normalized)));
    }

    public List<String> searchBroadcasters(String query) {
        String q = normalize(query);
        return channelRepository
            .findTop50ByBroadcasterContainingIgnoreCaseOrderByBroadcasterAsc(q == null ? "" : q)
            .stream()
            .map(Channel::getBroadcaster)
            .toList();
    }

    public boolean addAdmin(String broadcaster, String username) {
        Channel channel = getOrCreateChannel(broadcaster);
        boolean added = channel.addAdmin(username);
        if (added) {
            channelRepository.saveAndFlush(channel);
            messagingTemplate.convertAndSend(topicFor(broadcaster), "Admin added: " + username);
        }
        return added;
    }

    public boolean removeAdmin(String broadcaster, String username) {
        Channel channel = getOrCreateChannel(broadcaster);
        boolean removed = channel.removeAdmin(username);
        if (removed) {
            channelRepository.saveAndFlush(channel);
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
        return sortAndMapAssets(normalized, assetRepository.findByBroadcasterAndHiddenFalse(normalized));
    }

    public CanvasSettingsRequest getCanvasSettings(String broadcaster) {
        Channel channel = getOrCreateChannel(broadcaster);
        return new CanvasSettingsRequest(channel.getCanvasWidth(), channel.getCanvasHeight());
    }

    public CanvasSettingsRequest updateCanvasSettings(String broadcaster, CanvasSettingsRequest req) {
        Channel channel = getOrCreateChannel(broadcaster);
        channel.setCanvasWidth(req.getWidth());
        channel.setCanvasHeight(req.getHeight());
        channelRepository.save(channel);
        CanvasSettingsRequest response = new CanvasSettingsRequest(channel.getCanvasWidth(), channel.getCanvasHeight());
        messagingTemplate.convertAndSend(topicFor(broadcaster), CanvasEvent.updated(broadcaster, response));
        return response;
    }

    public Optional<AssetView> createAsset(String broadcaster, MultipartFile file) throws IOException {
        long fileSize = file.getSize();
        if (fileSize > uploadLimitBytes) {
            throw new ResponseStatusException(
                PAYLOAD_TOO_LARGE,
                String.format(
                    "Uploaded file is too large (%d bytes). Maximum allowed is %d bytes.",
                    fileSize,
                    uploadLimitBytes
                )
            );
        }
        Channel channel = getOrCreateChannel(broadcaster);
        byte[] bytes = file.getBytes();
        String mediaType = mediaDetectionService
            .detectAllowedMediaType(file, bytes)
            .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Unsupported media type"));

        OptimizedAsset optimized = mediaOptimizationService.optimizeAsset(bytes, mediaType);
        if (optimized == null) {
            return Optional.empty();
        }

        String safeName = Optional.ofNullable(file.getOriginalFilename())
            .map(this::sanitizeFilename)
            .filter((s) -> !s.isBlank())
            .orElse("asset_" + System.currentTimeMillis());

        boolean isAudio = optimized.mediaType().startsWith("audio/");
        boolean isCode = isCodeMediaType(optimized.mediaType()) || isCodeMediaType(mediaType);
        double defaultWidth = isAudio ? 400 : isCode ? 480 : 640;
        double defaultHeight = isAudio ? 80 : isCode ? 270 : 360;
        double width = optimized.width() > 0 ? optimized.width() : defaultWidth;
        double height = optimized.height() > 0 ? optimized.height() : defaultHeight;

        Asset asset = new Asset(channel.getBroadcaster(), safeName, "", width, height);
        asset.setOriginalMediaType(mediaType);
        asset.setMediaType(optimized.mediaType());

        assetStorageService.storeAsset(
            channel.getBroadcaster(),
            asset.getId(),
            optimized.bytes(),
            optimized.mediaType()
        );

        assetStorageService.storePreview(channel.getBroadcaster(), asset.getId(), optimized.previewBytes());
        asset.setPreview(optimized.previewBytes() != null ? asset.getId() + ".png" : "");

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

    public Optional<AssetView> createCodeAsset(String broadcaster, CodeAssetRequest request) {
        validateCodeAssetSource(request.getSource());
        Channel channel = getOrCreateChannel(broadcaster);
        byte[] bytes = request.getSource().getBytes(StandardCharsets.UTF_8);
        enforceUploadLimit(bytes.length);

        Asset asset = new Asset(channel.getBroadcaster(), request.getName().trim(), "", 480, 270);
        asset.setOriginalMediaType(DEFAULT_CODE_MEDIA_TYPE);
        asset.setMediaType(DEFAULT_CODE_MEDIA_TYPE);
        asset.setPreview("");
        asset.setSpeed(1.0);
        asset.setMuted(false);
        asset.setAudioLoop(false);
        asset.setAudioDelayMillis(0);
        asset.setAudioSpeed(1.0);
        asset.setAudioPitch(1.0);
        asset.setAudioVolume(1.0);
        asset.setZIndex(nextZIndex(channel.getBroadcaster()));

        try {
            assetStorageService.storeAsset(channel.getBroadcaster(), asset.getId(), bytes, DEFAULT_CODE_MEDIA_TYPE);
        } catch (IOException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Unable to store custom script", e);
        }

        assetRepository.save(asset);
        AssetView view = AssetView.from(channel.getBroadcaster(), asset);
        messagingTemplate.convertAndSend(topicFor(broadcaster), AssetEvent.created(broadcaster, view));
        return Optional.of(view);
    }

    public Optional<AssetView> updateCodeAsset(String broadcaster, String assetId, CodeAssetRequest request) {
        validateCodeAssetSource(request.getSource());
        String normalized = normalize(broadcaster);
        byte[] bytes = request.getSource().getBytes(StandardCharsets.UTF_8);
        enforceUploadLimit(bytes.length);

        return assetRepository
            .findById(assetId)
            .filter((asset) -> normalized.equals(asset.getBroadcaster()))
            .map((asset) -> {
                if (!isCodeMediaType(asset.getMediaType()) && !isCodeMediaType(asset.getOriginalMediaType())) {
                    throw new ResponseStatusException(BAD_REQUEST, "Asset is not a script");
                }
                asset.setName(request.getName().trim());
                asset.setOriginalMediaType(DEFAULT_CODE_MEDIA_TYPE);
                asset.setMediaType(DEFAULT_CODE_MEDIA_TYPE);
                try {
                    assetStorageService.storeAsset(broadcaster, asset.getId(), bytes, DEFAULT_CODE_MEDIA_TYPE);
                } catch (IOException e) {
                    throw new ResponseStatusException(BAD_REQUEST, "Unable to store custom script", e);
                }
                assetRepository.save(asset);
                AssetView view = AssetView.from(normalized, asset);
                messagingTemplate.convertAndSend(topicFor(broadcaster), AssetEvent.updated(broadcaster, view));
                return view;
            });
    }

    private String sanitizeFilename(String original) {
        String stripped = original.replaceAll("^.*[/\\\\]", "");
        return SAFE_FILENAME.matcher(stripped).replaceAll("_");
    }

    public Optional<AssetView> updateTransform(String broadcaster, String assetId, TransformRequest req) {
        String normalized = normalize(broadcaster);

        return assetRepository
            .findById(assetId)
            .filter((asset) -> normalized.equals(asset.getBroadcaster()))
            .map((asset) -> {
                AssetPatch.TransformSnapshot before = AssetPatch.capture(asset);
                validateTransform(req);

                asset.setX(req.getX());
                asset.setY(req.getY());
                asset.setWidth(req.getWidth());
                asset.setHeight(req.getHeight());
                asset.setRotation(req.getRotation());

                if (req.getZIndex() != null) asset.setZIndex(req.getZIndex());
                if (req.getSpeed() != null) asset.setSpeed(req.getSpeed());
                if (req.getMuted() != null && asset.isVideo()) asset.setMuted(req.getMuted());
                if (req.getAudioLoop() != null) asset.setAudioLoop(req.getAudioLoop());
                if (req.getAudioDelayMillis() != null) asset.setAudioDelayMillis(req.getAudioDelayMillis());
                if (req.getAudioSpeed() != null) asset.setAudioSpeed(req.getAudioSpeed());
                if (req.getAudioPitch() != null) asset.setAudioPitch(req.getAudioPitch());
                if (req.getAudioVolume() != null) asset.setAudioVolume(req.getAudioVolume());

                assetRepository.save(asset);

                AssetView view = AssetView.from(normalized, asset);
                AssetPatch patch = AssetPatch.fromTransform(before, asset, req);
                if (hasPatchChanges(patch)) {
                    messagingTemplate.convertAndSend(topicFor(broadcaster), AssetEvent.updated(broadcaster, patch));
                }
                return view;
            });
    }

    private void validateTransform(TransformRequest req) {
        Settings settings = settingsService.get();
        double maxSpeed = settings.getMaxAssetPlaybackSpeedFraction();
        double minSpeed = settings.getMinAssetPlaybackSpeedFraction();
        double minPitch = settings.getMinAssetAudioPitchFraction();
        double maxPitch = settings.getMaxAssetAudioPitchFraction();
        double minVolume = settings.getMinAssetVolumeFraction();
        double maxVolume = settings.getMaxAssetVolumeFraction();
        int canvasMaxSizePixels = settings.getMaxCanvasSideLengthPixels();

        if (req.getWidth() <= 0 || req.getWidth() > canvasMaxSizePixels) throw new ResponseStatusException(
            BAD_REQUEST,
            "Canvas width out of range [0 to " + canvasMaxSizePixels + "]"
        );
        if (req.getHeight() <= 0) throw new ResponseStatusException(
            BAD_REQUEST,
            "Canvas height out of range [0 to " + canvasMaxSizePixels + "]"
        );
        if (
            req.getSpeed() != null && (req.getSpeed() < minSpeed || req.getSpeed() > maxSpeed)
        ) throw new ResponseStatusException(BAD_REQUEST, "Speed out of range [" + minSpeed + " to " + maxSpeed + "]");
        if (req.getZIndex() != null && req.getZIndex() < 1) throw new ResponseStatusException(
            BAD_REQUEST,
            "zIndex must be >= 1"
        );
        if (req.getAudioDelayMillis() != null && req.getAudioDelayMillis() < 0) throw new ResponseStatusException(
            BAD_REQUEST,
            "Audio delay >= 0"
        );
        if (
            req.getAudioSpeed() != null && (req.getAudioSpeed() < minSpeed || req.getAudioSpeed() > maxSpeed)
        ) throw new ResponseStatusException(BAD_REQUEST, "Audio speed out of range");
        if (
            req.getAudioPitch() != null && (req.getAudioPitch() < minPitch || req.getAudioPitch() > maxPitch)
        ) throw new ResponseStatusException(BAD_REQUEST, "Audio pitch out of range");
        if (
            req.getAudioVolume() != null && (req.getAudioVolume() < minVolume || req.getAudioVolume() > maxVolume)
        ) throw new ResponseStatusException(
            BAD_REQUEST,
            "Audio volume out of range [" + minVolume + " to " + maxVolume + "]"
        );
    }

    public Optional<AssetView> triggerPlayback(String broadcaster, String assetId, PlaybackRequest req) {
        String normalized = normalize(broadcaster);
        return assetRepository
            .findById(assetId)
            .filter((a) -> normalized.equals(a.getBroadcaster()))
            .map((asset) -> {
                AssetView view = AssetView.from(normalized, asset);
                boolean play = req == null || req.getPlay();
                messagingTemplate.convertAndSend(topicFor(broadcaster), AssetEvent.play(broadcaster, view, play));
                return view;
            });
    }

    public Optional<AssetView> updateVisibility(String broadcaster, String assetId, VisibilityRequest request) {
        String normalized = normalize(broadcaster);
        return assetRepository
            .findById(assetId)
            .filter((a) -> normalized.equals(a.getBroadcaster()))
            .map((asset) -> {
                boolean wasHidden = asset.isHidden();
                boolean hidden = request.isHidden();
                if (wasHidden == hidden) {
                    return AssetView.from(normalized, asset);
                }

                asset.setHidden(hidden);
                assetRepository.save(asset);
                AssetView view = AssetView.from(normalized, asset);
                AssetPatch patch = AssetPatch.fromVisibility(asset);
                AssetView payload = hidden ? null : view;
                messagingTemplate.convertAndSend(
                    topicFor(broadcaster),
                    AssetEvent.visibility(broadcaster, patch, payload)
                );
                return view;
            });
    }

    public boolean deleteAsset(String assetId) {
        return assetRepository
            .findById(assetId)
            .map((asset) -> {
                assetRepository.delete(asset);
                assetStorageService.deleteAsset(asset);
                messagingTemplate.convertAndSend(
                    topicFor(asset.getBroadcaster()),
                    AssetEvent.deleted(asset.getBroadcaster(), assetId)
                );
                return true;
            })
            .orElse(false);
    }

    public Optional<AssetContent> getAssetContent(String assetId) {
        return assetRepository.findById(assetId).flatMap(assetStorageService::loadAssetFileSafely);
    }

    public Optional<AssetContent> getAssetPreview(String assetId, boolean includeHidden) {
        return assetRepository
            .findById(assetId)
            .filter((a) -> includeHidden || !a.isHidden())
            .flatMap(assetStorageService::loadPreviewSafely);
    }

    public boolean isAdmin(String broadcaster, String username) {
        return channelRepository
            .findById(normalize(broadcaster))
            .map(Channel::getAdmins)
            .map((admins) -> admins.contains(normalize(username)))
            .orElse(false);
    }

    public Collection<String> adminChannelsFor(String username) {
        if (username == null) return List.of();
        String login = username.toLowerCase();
        return channelRepository
            .findAll()
            .stream()
            .filter((c) -> c.getAdmins().contains(login))
            .map(Channel::getBroadcaster)
            .toList();
    }

    private String normalize(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private boolean isCodeMediaType(String mediaType) {
        if (mediaType == null || mediaType.isBlank()) {
            return false;
        }
        String normalized = mediaType.toLowerCase(Locale.ROOT);
        return normalized.startsWith("application/javascript") || normalized.startsWith("text/javascript");
    }

    private void validateCodeAssetSource(String source) {
        if (source == null || source.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Script source is required");
        }
    }

    private void enforceUploadLimit(long sizeBytes) {
        if (sizeBytes > uploadLimitBytes) {
            throw new ResponseStatusException(
                PAYLOAD_TOO_LARGE,
                String.format(
                    "Uploaded file is too large (%d bytes). Maximum allowed is %d bytes.",
                    sizeBytes,
                    uploadLimitBytes
                )
            );
        }
    }

    private String topicFor(String broadcaster) {
        return "/topic/channel/" + broadcaster.toLowerCase(Locale.ROOT);
    }

    private List<AssetView> sortAndMapAssets(String broadcaster, Collection<Asset> assets) {
        return assets
            .stream()
            .sorted(
                Comparator.comparingInt(Asset::getZIndex).thenComparing(
                    Asset::getCreatedAt,
                    Comparator.nullsFirst(Comparator.naturalOrder())
                )
            )
            .map((a) -> AssetView.from(broadcaster, a))
            .toList();
    }

    private int nextZIndex(String broadcaster) {
        return (
            assetRepository
                .findByBroadcaster(normalize(broadcaster))
                .stream()
                .mapToInt(Asset::getZIndex)
                .max()
                .orElse(0) +
            1
        );
    }

    private boolean hasPatchChanges(AssetPatch patch) {
        return (
            patch.x() != null ||
            patch.y() != null ||
            patch.width() != null ||
            patch.height() != null ||
            patch.rotation() != null ||
            patch.speed() != null ||
            patch.muted() != null ||
            patch.zIndex() != null ||
            patch.hidden() != null ||
            patch.audioLoop() != null ||
            patch.audioDelayMillis() != null ||
            patch.audioSpeed() != null ||
            patch.audioPitch() != null ||
            patch.audioVolume() != null
        );
    }
}
