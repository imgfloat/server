package dev.kruhlmann.imgfloat.service.media;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class MediaDetectionServiceTest {
    private final MediaDetectionService service = new MediaDetectionService();

    @Test
    void acceptsMagicBytesOverDeclaredType() throws IOException {
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "text/plain", png);

        assertThat(service.detectAllowedMediaType(file, file.getBytes())).contains("image/png");
    }

    @Test
    void fallsBackToFilenameAllowlist() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "picture.png", null, new byte[]{1, 2, 3});

        assertThat(service.detectAllowedMediaType(file, file.getBytes())).contains("image/png");
    }

    @Test
    void rejectsUnknownTypes() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "unknown.bin", null, new byte[]{1, 2, 3});

        assertThat(service.detectAllowedMediaType(file, file.getBytes())).isEmpty();
    }
}
