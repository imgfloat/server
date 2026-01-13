package dev.kruhlmann.imgfloat.service.media;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.jcodec.api.FrameGrab;
import org.jcodec.api.JCodecException;
import org.jcodec.common.io.ByteBufferSeekableByteChannel;
import org.jcodec.common.model.Picture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MediaOptimizationService {

    private static final int MIN_GIF_DELAY_MS = 20;
    private static final Logger logger = LoggerFactory.getLogger(MediaOptimizationService.class);
    private final MediaPreviewService previewService;

    public MediaOptimizationService(MediaPreviewService previewService) {
        this.previewService = previewService;
    }

    public OptimizedAsset optimizeAsset(byte[] bytes, String mediaType) throws IOException {
        if (mediaType == null || mediaType.isBlank() || bytes == null || bytes.length == 0) {
            return null;
        }
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
            byte[] preview = previewService.extractVideoPreview(bytes, mediaType);
            return new OptimizedAsset(bytes, mediaType, dimensions.width(), dimensions.height(), preview);
        }

        if (mediaType.startsWith("audio/")) {
            return new OptimizedAsset(bytes, mediaType, 0, 0, null);
        }

        if (mediaType.startsWith("model/")) {
            return new OptimizedAsset(bytes, mediaType, 0, 0, null);
        }

        if (mediaType.startsWith("application/javascript") || mediaType.startsWith("text/javascript")) {
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
            int baseDelay = frames
                .stream()
                .mapToInt((frame) -> normalizeDelay(frame.delayMs()))
                .reduce(this::greatestCommonDivisor)
                .orElse(100);
            int fps = Math.max(1, (int) Math.round(1000.0 / baseDelay));
            File temp = File.createTempFile("gif-convert", ".mp4");
            temp.deleteOnExit();
            try {
                var encoder = org.jcodec.api.awt.AWTSequenceEncoder.createSequenceEncoder(temp, fps);
                for (GifFrame frame : frames) {
                    BufferedImage image = ensureEvenDimensions(frame.image());
                    int repeats = Math.max(1, normalizeDelay(frame.delayMs()) / baseDelay);
                    for (int i = 0; i < repeats; i++) {
                        encoder.encodeImage(image);
                    }
                }
                encoder.finish();
                BufferedImage cover = ensureEvenDimensions(frames.get(0).image());
                byte[] video = Files.readAllBytes(temp.toPath());
                return new OptimizedAsset(
                    video,
                    "video/mp4",
                    cover.getWidth(),
                    cover.getHeight(),
                    previewService.encodePreview(cover)
                );
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
            var reader = readers.next();
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
            var root = metadata.getAsTree(format);
            var children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                var node = children.item(i);
                if ("GraphicControlExtension".equals(node.getNodeName()) && node.getAttributes() != null) {
                    var delay = node.getAttributes().getNamedItem("delayTime");
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
        try (
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageOutputStream ios = ImageIO.createImageOutputStream(baos)
        ) {
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

    private BufferedImage ensureEvenDimensions(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int evenWidth = width % 2 == 0 ? width : width + 1;
        int evenHeight = height % 2 == 0 ? height : height + 1;
        if (evenWidth == width && evenHeight == height) {
            return image;
        }
        BufferedImage padded = new BufferedImage(evenWidth, evenHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = padded.createGraphics();
        try {
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return padded;
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

    private record GifFrame(BufferedImage image, int delayMs) {}

    private record Dimension(int width, int height) {}
}
