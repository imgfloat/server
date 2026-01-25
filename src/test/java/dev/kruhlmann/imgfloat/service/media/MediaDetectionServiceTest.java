package dev.kruhlmann.imgfloat.service.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class MediaDetectionServiceTest {

    private final MediaDetectionService service = new MediaDetectionService();

    @Test
    void acceptsMagicBytesOverDeclaredType() throws IOException {
        byte[] png = new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47 };
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "text/plain", png);

        assertThat(service.detectAllowedMediaType(file, file.getBytes())).contains("image/png");
    }

    @Test
    void detectsApngEvenWhenNamedPng() throws IOException {
        byte[] apng = new byte[] {
            (byte) 0x89,
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
            0x00,
            0x00,
            0x00,
            0x00,
            'a',
            'c',
            'T',
            'L',
            0x00,
            0x00,
            0x00,
            0x00,
        };
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", apng);

        assertThat(service.detectAllowedMediaType(file, file.getBytes())).contains("image/apng");
    }

    @Test
    void fallsBackToFilenameAllowlist() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "picture.png", null, new byte[] { 1, 2, 3 });

        assertThat(service.detectAllowedMediaType(file, file.getBytes())).contains("image/png");
    }

    @Test
    void rejectsUnknownTypes() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "unknown.bin", null, new byte[] { 1, 2, 3 });

        assertThat(service.detectAllowedMediaType(file, file.getBytes())).isEmpty();
    }
}
