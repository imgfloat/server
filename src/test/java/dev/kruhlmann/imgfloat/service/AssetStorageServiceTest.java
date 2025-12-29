package dev.kruhlmann.imgfloat.service;

import dev.kruhlmann.imgfloat.service.media.AssetContent;
import dev.kruhlmann.imgfloat.model.Asset;
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
        Asset asset = new Asset("caster", "asset", "http://example.com", 10, 10);
        asset.setMediaType("image/png");

        service.storeAsset("caster", asset.getId(), bytes, "image/png");

        AssetContent loaded = service.loadAssetFile(asset).orElseThrow();
        assertThat(loaded.bytes()).containsExactly(bytes);
        assertThat(loaded.mediaType()).isEqualTo("image/png");
        assertThat(Files.exists(assets.resolve("caster").resolve(asset.getId() + ".png"))).isTrue();
    }

    @Test
    void ignoresEmptyPreview() throws IOException {
        service.storePreview("caster", "id", new byte[0]);
        assertThat(Files.list(previews).count()).isEqualTo(0);
    }

    @Test
    void storesAndLoadsPreviews() throws IOException {
        byte[] preview = new byte[]{9, 8, 7};
        Asset asset = new Asset("caster", "asset", "http://example.com", 10, 10);
        asset.setMediaType("image/png");

        service.storePreview("caster", asset.getId(), preview);
        assertThat(service.loadPreview(asset)).isPresent();
    }
}
