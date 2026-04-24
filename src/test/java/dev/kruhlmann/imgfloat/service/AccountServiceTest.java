package dev.kruhlmann.imgfloat.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.kruhlmann.imgfloat.model.AssetType;
import dev.kruhlmann.imgfloat.model.db.imgfloat.Asset;
import dev.kruhlmann.imgfloat.model.db.imgfloat.ScriptAssetFile;
import dev.kruhlmann.imgfloat.repository.AssetRepository;
import dev.kruhlmann.imgfloat.repository.ChannelRepository;
import dev.kruhlmann.imgfloat.repository.MarketplaceScriptHeartRepository;
import dev.kruhlmann.imgfloat.repository.ScriptAssetFileRepository;
import dev.kruhlmann.imgfloat.repository.SystemAdministratorRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AccountServiceTest {

    private ChannelDirectoryService channelDirectoryService;
    private AssetRepository assetRepository;
    private ChannelRepository channelRepository;
    private ScriptAssetFileRepository scriptAssetFileRepository;
    private MarketplaceScriptHeartRepository heartRepository;
    private SystemAdministratorRepository sysadminRepository;
    private AssetStorageService assetStorageService;
    private JdbcTemplate jdbcTemplate;
    private AuditLogService auditLogService;
    private AccountService service;

    @BeforeEach
    void setup() {
        channelDirectoryService = mock(ChannelDirectoryService.class);
        assetRepository = mock(AssetRepository.class);
        channelRepository = mock(ChannelRepository.class);
        scriptAssetFileRepository = mock(ScriptAssetFileRepository.class);
        heartRepository = mock(MarketplaceScriptHeartRepository.class);
        sysadminRepository = mock(SystemAdministratorRepository.class);
        assetStorageService = mock(AssetStorageService.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        auditLogService = mock(AuditLogService.class);

        service = new AccountService(
            channelDirectoryService, assetRepository, channelRepository,
            scriptAssetFileRepository, heartRepository, sysadminRepository,
            assetStorageService, jdbcTemplate, auditLogService
        );
    }

    @Test
    void deleteAccountNormalizesUsername() {
        when(assetRepository.findByBroadcaster("uppercase")).thenReturn(List.of());
        when(scriptAssetFileRepository.findByBroadcaster("uppercase")).thenReturn(List.of());

        service.deleteAccount("UPPERCASE");

        verify(assetRepository).findByBroadcaster("uppercase");
        verify(channelRepository).deleteById("uppercase");
    }

    @Test
    void deleteAccountSkipsBlankUsername() {
        service.deleteAccount("");
        service.deleteAccount("   ");
        service.deleteAccount(null);

        verify(assetRepository, never()).findByBroadcaster(any());
    }

    @Test
    void deleteAccountDeletesAllAssets() {
        Asset asset = new Asset("user", AssetType.IMAGE);
        when(assetRepository.findByBroadcaster("user")).thenReturn(List.of(asset));
        when(scriptAssetFileRepository.findByBroadcaster("user")).thenReturn(List.of());

        service.deleteAccount("user");

        verify(channelDirectoryService).deleteAsset(asset.getId(), "user");
    }

    @Test
    void deleteAccountDeletesScriptAssetFilesFromDisk() {
        when(assetRepository.findByBroadcaster("user")).thenReturn(List.of());
        ScriptAssetFile file = new ScriptAssetFile("user", AssetType.SCRIPT);
        file.setMediaType("application/javascript");
        when(scriptAssetFileRepository.findByBroadcaster("user")).thenReturn(List.of(file));

        service.deleteAccount("user");

        verify(assetStorageService).deleteAsset(eq("user"), eq(file.getId()), eq("application/javascript"), eq(false));
        verify(scriptAssetFileRepository).delete(file);
    }

    @Test
    void deleteAccountCleansUpAllRepositories() {
        when(assetRepository.findByBroadcaster("user")).thenReturn(List.of());
        when(scriptAssetFileRepository.findByBroadcaster("user")).thenReturn(List.of());

        service.deleteAccount("user");

        verify(heartRepository).deleteByUsername("user");
        verify(sysadminRepository).deleteByTwitchUsername("user");
        verify(channelRepository).deleteById("user");
        verify(auditLogService).deleteEntriesForBroadcaster("user");
    }

    @Test
    void deleteAccountCleansUpSessionTables() {
        when(assetRepository.findByBroadcaster("user")).thenReturn(List.of());
        when(scriptAssetFileRepository.findByBroadcaster("user")).thenReturn(List.of());

        service.deleteAccount("user");

        verify(jdbcTemplate).update(contains("SPRING_SESSION_ATTRIBUTES"), eq("user"));
        verify(jdbcTemplate).update(eq("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?"), eq("user"));
    }
}
