package com.imgfloat.app.service.media;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class MediaOptimizationServiceTest {
    private MediaOptimizationService service;

    @BeforeEach
    void setUp() {
        service = new MediaOptimizationService(new MediaPreviewService());
    }

    @Test
    void returnsNullForEmptyInput() throws IOException {
        assertThat(service.optimizeAsset(new byte[0], "image/png")).isNull();
    }

    @Test
    void optimizesPngImages() throws IOException {
        byte[] png = samplePng();

        OptimizedAsset optimized = service.optimizeAsset(png, "image/png");

        assertThat(optimized).isNotNull();
        assertThat(optimized.mediaType()).isEqualTo("image/png");
        assertThat(optimized.width()).isEqualTo(2);
        assertThat(optimized.height()).isEqualTo(2);
        assertThat(optimized.previewBytes()).isNull();
    }

    @Test
    void returnsNullForUnsupportedBytes() throws IOException {
        OptimizedAsset optimized = service.optimizeAsset(new byte[]{1, 2, 3}, "application/octet-stream");

        assertThat(optimized).isNull();
    }

    private byte[] samplePng() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        }
    }
}
