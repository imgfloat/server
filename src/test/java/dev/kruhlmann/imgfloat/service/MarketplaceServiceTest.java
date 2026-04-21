package dev.kruhlmann.imgfloat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.kruhlmann.imgfloat.model.api.response.ScriptMarketplaceEntry;
import dev.kruhlmann.imgfloat.model.db.imgfloat.MarketplaceScriptHeart;
import dev.kruhlmann.imgfloat.model.db.imgfloat.ScriptAsset;
import dev.kruhlmann.imgfloat.repository.AssetRepository;
import dev.kruhlmann.imgfloat.repository.MarketplaceScriptHeartRepository;
import dev.kruhlmann.imgfloat.repository.ScriptAssetFileRepository;
import dev.kruhlmann.imgfloat.repository.ScriptAssetRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MarketplaceServiceTest {

    private MarketplaceService service;
    private ScriptAssetRepository scriptAssetRepository;
    private MarketplaceScriptHeartRepository heartRepository;
    private MarketplaceScriptSeedLoader seedLoader;

    @BeforeEach
    void setup() throws Exception {
        scriptAssetRepository = mock(ScriptAssetRepository.class);
        AssetRepository assetRepository = mock(AssetRepository.class);
        ScriptAssetFileRepository scriptAssetFileRepository = mock(ScriptAssetFileRepository.class);
        AssetStorageService assetStorageService = mock(AssetStorageService.class);
        heartRepository = mock(MarketplaceScriptHeartRepository.class);

        when(scriptAssetRepository.findByIsPublicTrue()).thenReturn(List.of());
        when(heartRepository.countByScriptIds(anyList())).thenReturn(List.of());
        when(heartRepository.findByUsernameAndScriptIdIn(anyString(), anyList())).thenReturn(List.of());

        Path marketplaceRoot = Files.createTempDirectory("imgfloat-marketplace-test");
        Path scriptDir = marketplaceRoot.resolve("test-script");
        Files.createDirectories(scriptDir);
        Files.writeString(scriptDir.resolve("metadata.json"),
            "{\"name\":\"Test Script\",\"description\":\"A test script\"}");
        Files.writeString(scriptDir.resolve("source.js"), "exports.init = function() {};");

        seedLoader = new MarketplaceScriptSeedLoader(marketplaceRoot.toString());
        service = new MarketplaceService(
            seedLoader,
            scriptAssetRepository,
            assetRepository,
            scriptAssetFileRepository,
            assetStorageService,
            heartRepository
        );
    }

    @Test
    void listScriptsReturnsSeedEntries() {
        List<ScriptMarketplaceEntry> entries = service.listScripts(null, null);
        assertThat(entries).anyMatch(e -> "test-script".equals(e.id()));
    }

    @Test
    void listScriptsFiltersEntriesByQuery() {
        List<ScriptMarketplaceEntry> entries = service.listScripts("test", null);
        assertThat(entries).anyMatch(e -> "test-script".equals(e.id()));

        List<ScriptMarketplaceEntry> noMatch = service.listScripts("zzznomatch", null);
        assertThat(noMatch).noneMatch(e -> "test-script".equals(e.id()));
    }

    @Test
    void listScriptsIncludesPublicDatabaseScripts() {
        ScriptAsset publicScript = new ScriptAsset();
        publicScript.setId("db-script");
        publicScript.setName("DB Script");
        publicScript.setPublic(true);
        when(scriptAssetRepository.findByIsPublicTrue()).thenReturn(List.of(publicScript));
        when(heartRepository.countByScriptIds(anyList())).thenReturn(List.of());

        List<ScriptMarketplaceEntry> entries = service.listScripts(null, null);
        assertThat(entries).anyMatch(e -> "db-script".equals(e.id()));
    }

    @Test
    void toggleHeartAddsHeartWhenNotAlreadyHearted() {
        String scriptId = "test-script";
        String username = "user1";
        when(heartRepository.existsByScriptIdAndUsername(scriptId, username)).thenReturn(false);
        when(heartRepository.countByScriptId(scriptId)).thenReturn(1L);

        Optional<ScriptMarketplaceEntry> result = service.toggleHeart(scriptId, username);
        assertThat(result).isPresent();
    }

    @Test
    void toggleHeartReturnsEmptyForBlankInputs() {
        assertThat(service.toggleHeart(null, "user")).isEmpty();
        assertThat(service.toggleHeart("script", null)).isEmpty();
        assertThat(service.toggleHeart("", "user")).isEmpty();
    }

    @Test
    void listScriptsWithBlankQueryTreatsAsNoFilter() {
        List<ScriptMarketplaceEntry> withNull = service.listScripts(null, null);
        List<ScriptMarketplaceEntry> withBlank = service.listScripts("  ", null);
        assertThat(withBlank).hasSize(withNull.size());
    }
}
