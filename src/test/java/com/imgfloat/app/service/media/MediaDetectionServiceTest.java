package com.imgfloat.app.service.media;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class MediaDetectionServiceTest {
    private final MediaDetectionService service = new MediaDetectionService();

    @Test
    void prefersProvidedContentType() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", new byte[]{1, 2, 3});

        assertThat(service.detectMediaType(file, file.getBytes())).isEqualTo("image/png");
    }

    @Test
    void fallsBackToFilenameAndStream() throws IOException {
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};
        MockMultipartFile file = new MockMultipartFile("file", "picture.png", null, png);

        assertThat(service.detectMediaType(file, file.getBytes())).isEqualTo("image/png");
    }

    @Test
    void returnsOctetStreamForUnknownType() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "unknown.bin", null, new byte[]{1, 2, 3});

        assertThat(service.detectMediaType(file, file.getBytes())).isEqualTo("application/octet-stream");
    }
}
