package dev.kruhlmann.imgfloat.service;

import dev.kruhlmann.imgfloat.service.media.AssetContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssetStorageServiceTest {
    private AssetStorageService service;
    private Path assets;
    private Path previews;

    @BeforeEach
    void setUp() throws IOException {
        assets = Files.createTempDirectory("asset-storage-service");
        previews = Files.createTempDirectory("preview-storage-service");
        service = new AssetStorageService(assets.toString(), previews.toString());
    }

    @Test
    void refusesToStoreEmptyAsset() {
        assertThatThrownBy(() -> service.storeAsset("caster", "id", new byte[0], "image/png"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void storesAndLoadsAssets() throws IOException {
        byte[] bytes = new byte[]{1, 2, 3};

        String path = service.storeAsset("caster", "id", bytes, "image/png");
        assertThat(Files.exists(Path.of(path))).isTrue();

        AssetContent loaded = service.loadAssetFile(path, "image/png").orElseThrow();
        assertThat(loaded.bytes()).containsExactly(bytes);
        assertThat(loaded.mediaType()).isEqualTo("image/png");
    }

    @Test
    void ignoresEmptyPreview() throws IOException {
        assertThat(service.storePreview("caster", "id", new byte[0])).isNull();
    }

    @Test
    void storesAndLoadsPreviews() throws IOException {
        byte[] preview = new byte[]{9, 8, 7};

        String path = service.storePreview("caster", "id", preview);
        assertThat(service.loadPreview(path)).isPresent();
    }
}
