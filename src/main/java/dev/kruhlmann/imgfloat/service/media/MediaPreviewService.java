package dev.kruhlmann.imgfloat.service.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MediaPreviewService {

    private static final Logger logger = LoggerFactory.getLogger(MediaPreviewService.class);

    private final FfmpegService ffmpegService;

    public MediaPreviewService(FfmpegService ffmpegService) {
        this.ffmpegService = ffmpegService;
    }

    public byte[] extractVideoPreview(byte[] bytes, String mediaType) {
        return ffmpegService
            .extractVideoPreview(bytes)
            .orElseGet(() -> {
                logger.warn("Unable to capture video preview frame for {}", mediaType);
                return null;
            });
    }
}
