package com.imgfloat.app.service;

import com.imgfloat.app.model.Asset;
import com.imgfloat.app.model.AssetEvent;
import com.imgfloat.app.model.Channel;
import com.imgfloat.app.model.AssetView;
import com.imgfloat.app.model.CanvasSettingsRequest;
import com.imgfloat.app.model.TransformRequest;
import com.imgfloat.app.model.VisibilityRequest;
import com.imgfloat.app.repository.AssetRepository;
import com.imgfloat.app.repository.ChannelRepository;
import org.jcodec.api.FrameGrab;
import org.jcodec.api.JCodecException;
import org.jcodec.api.awt.AWTSequenceEncoder;
import org.jcodec.common.io.ByteBufferSeekableByteChannel;
import org.jcodec.common.model.Picture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
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
    private static final Logger logger = LoggerFactory.getLogger(ChannelDirectoryService.class);
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
        String mediaType = detectMediaType(file, bytes);

        OptimizedAsset optimized = optimizeAsset(bytes, mediaType);
        if (optimized == null) {
            return Optional.empty();
        }

        String name = Optional.ofNullable(file.getOriginalFilename())
                .map(filename -> filename.replaceAll("^.*[/\\\\]", ""))
                .filter(s -> !s.isBlank())
                .orElse("Asset " + System.currentTimeMillis());

        String dataUrl = "data:" + optimized.mediaType() + ";base64," + Base64.getEncoder().encodeToString(optimized.bytes());
        double width = optimized.width() > 0 ? optimized.width() : (optimized.mediaType().startsWith("audio/") ? 400 : 640);
        double height = optimized.height() > 0 ? optimized.height() : (optimized.mediaType().startsWith("audio/") ? 80 : 360);
        Asset asset = new Asset(channel.getBroadcaster(), name, dataUrl, width, height);
        asset.setOriginalMediaType(mediaType);
        asset.setMediaType(optimized.mediaType());
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
                        double clamped = Math.max(0.0, Math.min(1.0, request.getAudioVolume()));
                        asset.setAudioVolume(clamped);
                    }
                    assetRepository.save(asset);
                    AssetView view = AssetView.from(normalized, asset);
                    messagingTemplate.convertAndSend(topicFor(broadcaster), AssetEvent.updated(broadcaster, view));
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
                    messagingTemplate.convertAndSend(topicFor(broadcaster), AssetEvent.visibility(broadcaster, view));
                    return view;
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

    public Optional<AssetContent> getAssetContent(String broadcaster, String assetId) {
        String normalized = normalize(broadcaster);
        return assetRepository.findById(assetId)
                .filter(asset -> normalized.equals(asset.getBroadcaster()))
                .flatMap(this::decodeAssetData);
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

    private List<AssetView> sortAndMapAssets(String broadcaster, Collection<Asset> assets) {
        return assets.stream()
                .sorted(Comparator.comparingInt(Asset::getZIndex)
                        .thenComparing(Asset::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(asset -> AssetView.from(broadcaster, asset))
                .toList();
    }

    private Optional<AssetContent> decodeAssetData(Asset asset) {
        String url = asset.getUrl();
        if (url == null || !url.startsWith("data:")) {
            return Optional.empty();
        }
        int commaIndex = url.indexOf(',');
        if (commaIndex < 0) {
            return Optional.empty();
        }
        String metadata = url.substring(5, commaIndex);
        String[] parts = metadata.split(";", 2);
        String mediaType = parts.length > 0 && !parts[0].isBlank() ? parts[0] : "application/octet-stream";
        String encoded = url.substring(commaIndex + 1);
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            return Optional.of(new AssetContent(bytes, mediaType));
        } catch (IllegalArgumentException e) {
            logger.warn("Unable to decode asset data for {}", asset.getId(), e);
            return Optional.empty();
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
            return new OptimizedAsset(compressed, "image/png", image.getWidth(), image.getHeight());
        }

        if (mediaType.startsWith("image/")) {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                return null;
            }
            return new OptimizedAsset(bytes, mediaType, image.getWidth(), image.getHeight());
        }

        if (mediaType.startsWith("video/")) {
            var dimensions = extractVideoDimensions(bytes);
            return new OptimizedAsset(bytes, mediaType, dimensions.width(), dimensions.height());
        }

        if (mediaType.startsWith("audio/")) {
            return new OptimizedAsset(bytes, mediaType, 0, 0);
        }

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image != null) {
            return new OptimizedAsset(bytes, mediaType, image.getWidth(), image.getHeight());
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
                return new OptimizedAsset(video, "video/mp4", cover.getWidth(), cover.getHeight());
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

    public record AssetContent(byte[] bytes, String mediaType) { }

    private record OptimizedAsset(byte[] bytes, String mediaType, int width, int height) { }

    private record GifFrame(BufferedImage image, int delayMs) { }

    private record Dimension(int width, int height) { }
}
