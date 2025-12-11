package com.imgfloat.app.service;

import com.imgfloat.app.model.Asset;
import com.imgfloat.app.model.AssetEvent;
import com.imgfloat.app.model.AssetPatch;
import com.imgfloat.app.model.Channel;
import com.imgfloat.app.model.AssetView;
import com.imgfloat.app.model.CanvasSettingsRequest;
import com.imgfloat.app.model.PlaybackRequest;
import com.imgfloat.app.model.TransformRequest;
import com.imgfloat.app.model.VisibilityRequest;
import com.imgfloat.app.repository.AssetRepository;
import com.imgfloat.app.repository.ChannelRepository;
import org.jcodec.api.FrameGrab;
import org.jcodec.api.JCodecException;
import org.jcodec.api.awt.AWTSequenceEncoder;
import org.jcodec.common.io.ByteBufferSeekableByteChannel;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.file.InvalidPathException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.IIOImage;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.ImageInputStream;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@Service
public class ChannelDirectoryService {
    private static final int MIN_GIF_DELAY_MS = 20;
    private static final String PREVIEW_MEDIA_TYPE = "image/png";
    private static final Path PREVIEW_ROOT = Paths.get("previews");
    private static final Path ASSET_ROOT = Paths.get("assets");
    private static final Logger logger = LoggerFactory.getLogger(ChannelDirectoryService.class);
    private final ChannelRepository channelRepository;
    private final AssetRepository assetRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final TaskExecutor assetTaskExecutor;

    public ChannelDirectoryService(ChannelRepository channelRepository,
                                   AssetRepository assetRepository,
                                   SimpMessagingTemplate messagingTemplate,
                                   @Qualifier("assetTaskExecutor") TaskExecutor assetTaskExecutor) {
        this.channelRepository = channelRepository;
        this.assetRepository = assetRepository;
        this.messagingTemplate = messagingTemplate;
        this.assetTaskExecutor = assetTaskExecutor;
    }

    public Channel getOrCreateChannel(String broadcaster) {
        String normalized = normalize(broadcaster);
        return channelRepository.findById(normalized)
                .orElseGet(() -> channelRepository.save(new Channel(normalized)));
    }

    public List<String> searchBroadcasters(String query) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery == null || normalizedQuery.isBlank()) {
            return channelRepository.findTop50ByOrderByBroadcasterAsc().stream()
                    .map(Channel::getBroadcaster)
                    .toList();
        }
        return channelRepository.findTop50ByBroadcasterContainingIgnoreCaseOrderByBroadcasterAsc(normalizedQuery).stream()
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
        byte[] bytes;
        try (InputStream stream = file.getInputStream()) {
            bytes = stream.readAllBytes();
        }
        String mediaType = detectMediaType(file, bytes);

        String name = Optional.ofNullable(file.getOriginalFilename())
                .map(filename -> filename.replaceAll("^.*[/\\\\]", ""))
                .filter(s -> !s.isBlank())
                .orElse("Asset " + System.currentTimeMillis());
        Asset asset = buildPlaceholderAsset(channel, name, bytes, mediaType);
        if (asset == null) {
            return Optional.empty();
        }
        if (!shouldOptimizeAsync(mediaType)) {
            OptimizedAsset optimized = optimizeAsset(bytes, mediaType);
            if (optimized == null) {
                return Optional.empty();
            }
            applyOptimizedAsset(channel.getBroadcaster(), asset, optimized);
        }

        assetRepository.save(asset);
        AssetView view = AssetView.from(channel.getBroadcaster(), asset);
        messagingTemplate.convertAndSend(topicFor(broadcaster), AssetEvent.created(broadcaster, view));

        if (shouldOptimizeAsync(mediaType)) {
            enqueueOptimization(channel.getBroadcaster(), asset.getId(), bytes, mediaType);
        }

        return Optional.of(view);
    }

    private Asset buildPlaceholderAsset(Channel channel, String name, byte[] bytes, String mediaType) {
        Dimension dimension = inferDimensions(bytes, mediaType);
        double width = dimension.width() > 0 ? dimension.width() : (mediaType.startsWith("audio/") ? 400 : 640);
        double height = dimension.height() > 0 ? dimension.height() : (mediaType.startsWith("audio/") ? 80 : 360);
        Asset asset = new Asset(channel.getBroadcaster(), name, "", width, height);
        asset.setOriginalMediaType(mediaType);
        asset.setMediaType(optimized.mediaType());
        asset.setPreview(null);
        asset.setUrl(storeAsset(channel.getBroadcaster(), asset.getId(), optimized.bytes(), optimized.mediaType()));
        asset.setSpeed(1.0);
        asset.setMuted(mediaType.startsWith("video/"));
        asset.setAudioLoop(false);
        asset.setAudioDelayMillis(0);
        asset.setAudioSpeed(1.0);
        asset.setAudioPitch(1.0);
        asset.setAudioVolume(1.0);
        asset.setZIndex(nextZIndex(channel.getBroadcaster()));
        return asset;
    }

    private boolean shouldOptimizeAsync(String mediaType) {
        return "image/gif".equalsIgnoreCase(mediaType) || mediaType.startsWith("video/");
    }

    private void enqueueOptimization(String broadcaster, String assetId, byte[] bytes, String mediaType) {
        assetTaskExecutor.execute(() -> {
            try {
                OptimizedAsset optimized = optimizeAsset(bytes, mediaType);
                if (optimized == null) {
                    return;
                }
                assetRepository.findById(assetId)
                        .filter(asset -> broadcaster.equals(asset.getBroadcaster()))
                        .ifPresent(asset -> {
                            boolean changed = applyOptimizedAsset(broadcaster, asset, optimized);
                            if (changed) {
                                assetRepository.save(asset);
                                AssetView view = AssetView.from(broadcaster, asset);
                                messagingTemplate.convertAndSend(topicFor(broadcaster), AssetEvent.updated(broadcaster, view));
                            }
                        });
            } catch (Exception e) {
                logger.warn("Unable to optimize asset {}", assetId, e);
            }
        });
    }

    private boolean applyOptimizedAsset(String broadcaster, Asset asset, OptimizedAsset optimized) {
        if (optimized == null) {
            return false;
        }
        boolean changed = false;
        String optimizedUrl = toDataUrl(optimized.bytes(), optimized.mediaType());
        if (!optimizedUrl.equals(asset.getUrl())) {
            asset.setUrl(optimizedUrl);
            changed = true;
        }
        if (!Objects.equals(asset.getMediaType(), optimized.mediaType())) {
            asset.setMediaType(optimized.mediaType());
            changed = true;
        }
        if (optimized.width() > 0 && optimized.height() > 0) {
            asset.setWidth(optimized.width());
            asset.setHeight(optimized.height());
            changed = true;
        }
        asset.setMuted(optimized.mediaType().startsWith("video/"));

        String previousPreview = asset.getPreview();
        try {
            asset.setPreview(storePreview(broadcaster, asset.getId(), optimized.previewBytes()));
            if (!Objects.equals(previousPreview, asset.getPreview())) {
                deletePreviewFile(previousPreview);
                changed = changed || asset.getPreview() != null;
            }
        } catch (IOException e) {
            logger.warn("Unable to store preview for asset {}", asset.getId(), e);
        }
        return changed;
    }

    private Dimension inferDimensions(byte[] bytes, String mediaType) {
        try {
            if (mediaType.startsWith("video/")) {
                return extractVideoDimensions(bytes);
            }
            if (mediaType.startsWith("image/")) {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
                if (image != null) {
                    return new Dimension(image.getWidth(), image.getHeight());
                }
            }
        } catch (IOException e) {
            logger.warn("Unable to infer dimensions for {}", mediaType, e);
        }
        return new Dimension(0, 0);
    }

    private String toDataUrl(byte[] bytes, String mediaType) {
        return "data:" + mediaType + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    public Optional<AssetView> updateTransform(String broadcaster, String assetId, TransformRequest request) {
        String normalized = normalize(broadcaster);
        return assetRepository.findById(assetId)
                .filter(asset -> normalized.equals(asset.getBroadcaster()))
                .map(asset -> {
                    asset.setX(request.getX());
                    asset.setY(request.getY());
                    asset.setWidth(request.getWidth());
                    asset.setHeight(request.getHeight());
                    asset.setRotation(request.getRotation());
                    if (request.getZIndex() != null) {
                        asset.setZIndex(request.getZIndex());
                    }
                    if (request.getSpeed() != null && request.getSpeed() >= 0) {
                        asset.setSpeed(request.getSpeed());
                    }
                    if (request.getMuted() != null && asset.isVideo()) {
                        asset.setMuted(request.getMuted());
                    }
                    if (request.getAudioLoop() != null) {
                        asset.setAudioLoop(request.getAudioLoop());
                    }
                    if (request.getAudioDelayMillis() != null && request.getAudioDelayMillis() >= 0) {
                        asset.setAudioDelayMillis(request.getAudioDelayMillis());
                    }
                    if (request.getAudioSpeed() != null && request.getAudioSpeed() >= 0) {
                        asset.setAudioSpeed(request.getAudioSpeed());
                    }
                    if (request.getAudioPitch() != null && request.getAudioPitch() > 0) {
                        asset.setAudioPitch(request.getAudioPitch());
                    }
                    if (request.getAudioVolume() != null && request.getAudioVolume() >= 0) {
                        double clamped = Math.max(0.0, Math.min(2.0, request.getAudioVolume()));
                        asset.setAudioVolume(clamped);
                    }
                    assetRepository.save(asset);
                    AssetView view = AssetView.from(normalized, asset);
                    AssetPatch patch = AssetPatch.fromTransform(asset);
                    messagingTemplate.convertAndSend(topicFor(broadcaster), AssetEvent.updated(broadcaster, patch));
                    return view;
                });
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
                    deletePreviewFile(asset.getPreview());
                    deleteAssetFile(asset.getUrl());
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
                    Optional<AssetContent> preview = loadPreview(asset.getPreview())
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
        return channelRepository.findByAdminsContaining(login).stream()
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
        if (asset.getUrl() == null) {
            return Optional.empty();
        }

        if (asset.getUrl().startsWith("data:")) {
            return decodeDataUrl(asset.getUrl())
                    .flatMap(content -> backfillAssetStorage(asset, content));
        }

        return loadAssetFromStore(asset)
                .or(() -> {
                    logger.warn("Unable to read asset data for {}", asset.getId());
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

    private Optional<AssetContent> loadPreview(String previewPath) {
        if (previewPath == null || previewPath.isBlank()) {
            return Optional.empty();
        }
        try {
            Path path = Paths.get(previewPath);
            if (!Files.exists(path)) {
                return Optional.empty();
            }
            try {
                return Optional.of(new AssetContent(Files.readAllBytes(path), PREVIEW_MEDIA_TYPE));
            } catch (IOException e) {
                logger.warn("Unable to read preview from {}", previewPath, e);
                return Optional.empty();
            }
        } catch (InvalidPathException e) {
            logger.debug("Preview path {} is not a file path; skipping", previewPath);
            return Optional.empty();
        }
    }

    private String storeAsset(String broadcaster, String assetId, byte[] bytes, String mediaType) throws IOException {
        Path directory = ASSET_ROOT.resolve(normalize(broadcaster));
        Files.createDirectories(directory);
        String extension = extensionFor(mediaType);
        Path assetFile = directory.resolve(assetId + (extension == null ? "" : ("." + extension)));
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            Files.copy(in, assetFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return assetFile.toUri().toString();
    }

    private Optional<AssetContent> loadAssetFromStore(Asset asset) {
        String location = asset.getUrl();
        if (location == null || location.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = URI.create(location);
            Path path;
            if (uri.getScheme() == null) {
                path = Paths.get(location);
            } else if ("file".equalsIgnoreCase(uri.getScheme())) {
                path = Paths.get(uri);
            } else {
                logger.warn("Unsupported asset URI scheme {} for {}", uri.getScheme(), asset.getId());
                return Optional.empty();
            }
            if (path == null || !Files.exists(path)) {
                logger.warn("Asset location {} is not readable", location);
                return Optional.empty();
            }
            try (InputStream stream = Files.newInputStream(path)) {
                byte[] bytes = stream.readAllBytes();
                String mediaType = asset.getMediaType() == null ? "application/octet-stream" : asset.getMediaType();
                return Optional.of(new AssetContent(bytes, mediaType));
            }
        } catch (IOException | IllegalArgumentException e) {
            logger.warn("Unable to read asset data from {}", location, e);
            return Optional.empty();
        }
    }

    private Optional<AssetContent> backfillAssetStorage(Asset asset, AssetContent content) {
        try {
            String storedLocation = storeAsset(asset.getBroadcaster(), asset.getId(), content.bytes(),
                    asset.getMediaType() == null ? content.mediaType() : asset.getMediaType());
            asset.setUrl(storedLocation);
            assetRepository.save(asset);
            return Optional.of(new AssetContent(content.bytes(),
                    asset.getMediaType() == null ? content.mediaType() : asset.getMediaType()));
        } catch (IOException e) {
            logger.warn("Unable to backfill storage for {}", asset.getId(), e);
            return Optional.empty();
        }
    }

    private void deleteAssetFile(String location) {
        if (location == null || location.isBlank()) {
            return;
        }
        try {
            URI uri = URI.create(location);
            Path path;
            if (uri.getScheme() == null) {
                path = Paths.get(location);
            } else if ("file".equalsIgnoreCase(uri.getScheme())) {
                path = Paths.get(uri);
            } else {
                return;
            }
            if (path != null) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    logger.warn("Unable to delete asset file {}", path, e);
                }
            }
        } catch (IllegalArgumentException e) {
            logger.debug("Asset location {} is not a file path; skipping delete", location);
        }
    }

    private String storePreview(String broadcaster, String assetId, byte[] previewBytes) throws IOException {
        if (previewBytes == null || previewBytes.length == 0) {
            return null;
        }
        Path directory = PREVIEW_ROOT.resolve(normalize(broadcaster));
        Files.createDirectories(directory);
        Path previewFile = directory.resolve(assetId + ".png");
        Files.write(previewFile, previewBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        return previewFile.toString();
    }

    private void deletePreviewFile(String previewPath) {
        if (previewPath == null || previewPath.isBlank()) {
            return;
        }
        try {
            Path path = Paths.get(previewPath);
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                logger.warn("Unable to delete preview file {}", previewPath, e);
            }
        } catch (InvalidPathException e) {
            logger.debug("Preview value {} is not a file path; nothing to delete", previewPath);
        }
    }

    private int nextZIndex(String broadcaster) {
        return assetRepository.findByBroadcaster(normalize(broadcaster)).stream()
                .mapToInt(Asset::getZIndex)
                .max()
                .orElse(0) + 1;
    }

    private String detectMediaType(MultipartFile file, byte[] bytes) {
        String contentType = Optional.ofNullable(file.getContentType()).orElse("application/octet-stream");
        if (!"application/octet-stream".equals(contentType) && !contentType.isBlank()) {
            return contentType;
        }

        try (var stream = new ByteArrayInputStream(bytes)) {
            String guessed = URLConnection.guessContentTypeFromStream(stream);
            if (guessed != null && !guessed.isBlank()) {
                return guessed;
            }
        } catch (IOException e) {
            logger.warn("Unable to detect content type from stream", e);
        }

        return Optional.ofNullable(file.getOriginalFilename())
                .map(name -> name.replaceAll("^.*\\.", "").toLowerCase())
                .map(ext -> switch (ext) {
                    case "png" -> "image/png";
                    case "jpg", "jpeg" -> "image/jpeg";
                    case "gif" -> "image/gif";
                    case "mp4" -> "video/mp4";
                    case "webm" -> "video/webm";
                    case "mov" -> "video/quicktime";
                    case "mp3" -> "audio/mpeg";
                    case "wav" -> "audio/wav";
                    case "ogg" -> "audio/ogg";
                    default -> "application/octet-stream";
                })
                .orElse("application/octet-stream");
    }

    private String extensionFor(String mediaType) {
        if (mediaType == null) {
            return null;
        }
        return switch (mediaType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/gif" -> "gif";
            case "video/mp4" -> "mp4";
            case "video/webm" -> "webm";
            case "video/quicktime" -> "mov";
            case "audio/mpeg" -> "mp3";
            case "audio/wav" -> "wav";
            case "audio/ogg" -> "ogg";
            default -> null;
        };
    }

    private OptimizedAsset optimizeAsset(byte[] bytes, String mediaType) throws IOException {
        if ("image/gif".equalsIgnoreCase(mediaType)) {
            OptimizedAsset transcoded = transcodeGifToVideo(bytes);
            if (transcoded != null) {
                return transcoded;
            }
        }

        if (mediaType.startsWith("image/") && !"image/gif".equalsIgnoreCase(mediaType)) {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                return null;
            }
            byte[] compressed = compressPng(image);
            return new OptimizedAsset(compressed, "image/png", image.getWidth(), image.getHeight(), null);
        }

        if (mediaType.startsWith("image/")) {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                return null;
            }
            return new OptimizedAsset(bytes, mediaType, image.getWidth(), image.getHeight(), null);
        }

        if (mediaType.startsWith("video/")) {
            var dimensions = extractVideoDimensions(bytes);
            byte[] preview = extractVideoPreview(bytes, mediaType);
            return new OptimizedAsset(bytes, mediaType, dimensions.width(), dimensions.height(), preview);
        }

        if (mediaType.startsWith("audio/")) {
            return new OptimizedAsset(bytes, mediaType, 0, 0, null);
        }

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image != null) {
            return new OptimizedAsset(bytes, mediaType, image.getWidth(), image.getHeight(), null);
        }
        return null;
    }

    private OptimizedAsset transcodeGifToVideo(byte[] bytes) {
        try {
            List<GifFrame> frames = readGifFrames(bytes);
            if (frames.isEmpty()) {
                return null;
            }
            int baseDelay = frames.stream()
                    .mapToInt(frame -> normalizeDelay(frame.delayMs()))
                    .reduce(this::greatestCommonDivisor)
                    .orElse(100);
            int fps = Math.max(1, (int) Math.round(1000.0 / baseDelay));
            File temp = File.createTempFile("gif-convert", ".mp4");
            temp.deleteOnExit();
            try {
                AWTSequenceEncoder encoder = AWTSequenceEncoder.createSequenceEncoder(temp, fps);
                for (GifFrame frame : frames) {
                    int repeats = Math.max(1, normalizeDelay(frame.delayMs()) / baseDelay);
                    for (int i = 0; i < repeats; i++) {
                        encoder.encodeImage(frame.image());
                    }
                }
                encoder.finish();
                BufferedImage cover = frames.get(0).image();
                byte[] video = Files.readAllBytes(temp.toPath());
                return new OptimizedAsset(video, "video/mp4", cover.getWidth(), cover.getHeight(), encodePreview(cover));
            } finally {
                Files.deleteIfExists(temp.toPath());
            }
        } catch (IOException e) {
            logger.warn("Unable to transcode GIF to video", e);
            return null;
        }
    }

    private List<GifFrame> readGifFrames(byte[] bytes) throws IOException {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) {
                return List.of();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, false, false);
                int count = reader.getNumImages(true);
                var frames = new java.util.ArrayList<GifFrame>(count);
                for (int i = 0; i < count; i++) {
                    BufferedImage image = reader.read(i);
                    IIOMetadata metadata = reader.getImageMetadata(i);
                    int delay = extractDelayMs(metadata);
                    frames.add(new GifFrame(image, delay));
                }
                return frames;
            } finally {
                reader.dispose();
            }
        }
    }

    private int extractDelayMs(IIOMetadata metadata) {
        if (metadata == null) {
            return 100;
        }
        try {
            String format = metadata.getNativeMetadataFormatName();
            Node root = metadata.getAsTree(format);
            NodeList children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if ("GraphicControlExtension".equals(node.getNodeName()) && node.getAttributes() != null) {
                    Node delay = node.getAttributes().getNamedItem("delayTime");
                    if (delay != null) {
                        int hundredths = Integer.parseInt(delay.getNodeValue());
                        return Math.max(hundredths * 10, MIN_GIF_DELAY_MS);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Unable to parse GIF delay", e);
        }
        return 100;
    }

    private int normalizeDelay(int delayMs) {
        return Math.max(delayMs, MIN_GIF_DELAY_MS);
    }

    private int greatestCommonDivisor(int a, int b) {
        if (b == 0) {
            return Math.max(a, 1);
        }
        return greatestCommonDivisor(b, a % b);
    }

    private byte[] compressPng(BufferedImage image) throws IOException {
        var writers = ImageIO.getImageWritersByFormatName("png");
        if (!writers.hasNext()) {
            logger.warn("No PNG writer available; skipping compression");
            try (ByteArrayOutputStream fallback = new ByteArrayOutputStream()) {
                ImageIO.write(image, "png", fallback);
                return fallback.toByteArray();
            }
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(1.0f);
            }
            writer.write(null, new IIOImage(image, null, null), param);
            return baos.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private byte[] encodePreview(BufferedImage image) {
        if (image == null) {
            return null;
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            logger.warn("Unable to encode preview image", e);
            return null;
        }
    }

    private Dimension extractVideoDimensions(byte[] bytes) {
        try (var channel = new ByteBufferSeekableByteChannel(ByteBuffer.wrap(bytes), bytes.length)) {
            FrameGrab grab = FrameGrab.createFrameGrab(channel);
            Picture frame = grab.getNativeFrame();
            if (frame != null) {
                return new Dimension(frame.getWidth(), frame.getHeight());
            }
        } catch (IOException | JCodecException e) {
            logger.warn("Unable to read video dimensions", e);
        }
        return new Dimension(640, 360);
    }

    private byte[] extractVideoPreview(byte[] bytes, String mediaType) {
        try (var channel = new ByteBufferSeekableByteChannel(ByteBuffer.wrap(bytes), bytes.length)) {
            FrameGrab grab = FrameGrab.createFrameGrab(channel);
            Picture frame = grab.getNativeFrame();
            if (frame == null) {
                return null;
            }
            BufferedImage image = AWTUtil.toBufferedImage(frame);
            return encodePreview(image);
        } catch (IOException | JCodecException e) {
            logger.warn("Unable to capture video preview frame for {}", mediaType, e);
            return null;
        }
    }

    public record AssetContent(byte[] bytes, String mediaType) { }

    private record OptimizedAsset(byte[] bytes, String mediaType, int width, int height, byte[] previewBytes) { }

    private record GifFrame(BufferedImage image, int delayMs) { }

    private record Dimension(int width, int height) { }
}
