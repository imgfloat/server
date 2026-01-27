package dev.kruhlmann.imgfloat.service.media;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MediaOptimizationService {

    private static final Logger logger = LoggerFactory.getLogger(MediaOptimizationService.class);
    private static final FfmpegService.VideoDimensions DEFAULT_VIDEO_DIMENSIONS = new FfmpegService.VideoDimensions(
        640,
        360
    );

    private final MediaPreviewService previewService;
    private final FfmpegService ffmpegService;

    public MediaOptimizationService(MediaPreviewService previewService, FfmpegService ffmpegService) {
        this.previewService = previewService;
        this.ffmpegService = ffmpegService;
    }

    public OptimizedAsset optimizeAsset(byte[] bytes, String mediaType) throws IOException {
        if (mediaType == null || mediaType.isBlank() || bytes == null || bytes.length == 0) {
            return null;
        }
        if (isApng(mediaType, bytes)) {
            OptimizedAsset apngAsset = optimizeApng(bytes, mediaType);
            if (apngAsset != null) {
                return apngAsset;
            }
        }
        if ("image/gif".equalsIgnoreCase(mediaType)) {
            OptimizedAsset transcoded = transcodeGifToVideo(bytes);
            if (transcoded != null) {
                return transcoded;
            }
        }
        if (mediaType.startsWith("image/")) {
            return optimizeImage(bytes, mediaType);
        }

        if (mediaType.startsWith("video/")) {
            FfmpegService.VideoDimensions dimensions = ffmpegService
                .extractVideoDimensions(bytes)
                .orElse(DEFAULT_VIDEO_DIMENSIONS);
            byte[] preview = previewService.extractVideoPreview(bytes, mediaType);
            return new OptimizedAsset(bytes, mediaType, dimensions.width(), dimensions.height(), preview);
        }

        if (mediaType.startsWith("audio/")) {
            return new OptimizedAsset(bytes, mediaType, 0, 0, null);
        }

        if (mediaType.startsWith("font/")) {
            return new OptimizedAsset(bytes, mediaType, 0, 0, null);
        }

        if (mediaType.startsWith("model/")) {
            return new OptimizedAsset(bytes, mediaType, 0, 0, null);
        }

        if (mediaType.startsWith("application/javascript") || mediaType.startsWith("text/javascript")) {
            return new OptimizedAsset(bytes, mediaType, 0, 0, null);
        }

        return optimizeImage(bytes, mediaType);
    }

    private boolean isApng(String mediaType, byte[] bytes) {
        if (mediaType == null) {
            return false;
        }
        if ("image/apng".equalsIgnoreCase(mediaType)) {
            return true;
        }
        return "image/png".equalsIgnoreCase(mediaType) && ApngDetector.isApng(bytes);
    }

    private OptimizedAsset optimizeApng(byte[] bytes, String mediaType) {
        return ffmpegService
            .transcodeApngToGif(bytes)
            .map(this::transcodeGifToVideo)
            .orElseGet(() -> {
                logger.warn("Unable to transcode APNG to GIF via ffmpeg");
                return null;
            });
    }

    private OptimizedAsset transcodeGifToVideo(byte[] bytes) {
        return ffmpegService
            .transcodeGifToWebm(bytes)
            .map((videoBytes) -> {
                FfmpegService.VideoDimensions dimensions = ffmpegService
                    .extractVideoDimensions(videoBytes)
                    .orElse(DEFAULT_VIDEO_DIMENSIONS);
                byte[] preview = previewService.extractVideoPreview(videoBytes, "video/webm");
                return new OptimizedAsset(videoBytes, "video/webm", dimensions.width(), dimensions.height(), preview);
            })
            .orElseGet(() -> {
                logger.warn("Unable to transcode GIF to video via ffmpeg");
                return null;
            });
    }

    private OptimizedAsset optimizeImage(byte[] bytes, String mediaType) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) {
            return null;
        }
        return new OptimizedAsset(bytes, mediaType, image.getWidth(), image.getHeight(), null);
    }
}
