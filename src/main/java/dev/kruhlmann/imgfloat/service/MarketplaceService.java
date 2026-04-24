package dev.kruhlmann.imgfloat.service;

import dev.kruhlmann.imgfloat.model.api.response.ScriptMarketplaceEntry;
import dev.kruhlmann.imgfloat.model.db.imgfloat.Asset;
import dev.kruhlmann.imgfloat.model.db.imgfloat.MarketplaceScriptHeart;
import dev.kruhlmann.imgfloat.model.db.imgfloat.ScriptAsset;
import dev.kruhlmann.imgfloat.model.db.imgfloat.ScriptAssetFile;
import dev.kruhlmann.imgfloat.repository.AssetRepository;
import dev.kruhlmann.imgfloat.repository.MarketplaceScriptHeartRepository;
import dev.kruhlmann.imgfloat.repository.ScriptAssetFileRepository;
import dev.kruhlmann.imgfloat.repository.ScriptAssetRepository;
import dev.kruhlmann.imgfloat.service.media.AssetContent;
import dev.kruhlmann.imgfloat.util.AllowedDomainNormalizer;
import dev.kruhlmann.imgfloat.util.StringNormalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles the public marketplace: listing scripts, toggling hearts, and
 * serving logos.  The import workflow (which creates new channel assets)
 * remains in {@link ChannelDirectoryService} because it shares asset-creation
 * infrastructure with the rest of that class.
 */
@Service
public class MarketplaceService {

    private static final Logger LOG = LoggerFactory.getLogger(MarketplaceService.class);
    private static final String LOGO_URL_PREFIX = "/api/marketplace/scripts/";

    private final MarketplaceScriptSeedLoader seedLoader;
    private final ScriptAssetRepository scriptAssetRepository;
    private final AssetRepository assetRepository;
    private final ScriptAssetFileRepository scriptAssetFileRepository;
    private final AssetStorageService assetStorageService;
    private final MarketplaceScriptHeartRepository heartRepository;

    public MarketplaceService(
        MarketplaceScriptSeedLoader seedLoader,
        ScriptAssetRepository scriptAssetRepository,
        AssetRepository assetRepository,
        ScriptAssetFileRepository scriptAssetFileRepository,
        AssetStorageService assetStorageService,
        MarketplaceScriptHeartRepository heartRepository
    ) {
        this.seedLoader = seedLoader;
        this.scriptAssetRepository = scriptAssetRepository;
        this.assetRepository = assetRepository;
        this.scriptAssetFileRepository = scriptAssetFileRepository;
        this.assetStorageService = assetStorageService;
        this.heartRepository = heartRepository;
    }

    public List<ScriptMarketplaceEntry> listScripts(String query, String sessionUsername) {
        String normalized = query == null ? null : StringNormalizer.normalize(query);
        if (normalized != null && normalized.isBlank()) {
            normalized = null;
        }

        List<ScriptMarketplaceEntry> entries = new ArrayList<>(seedLoader.listEntriesForQuery(normalized));

        List<ScriptAsset> publicScripts;
        try {
            publicScripts = scriptAssetRepository.findByIsPublicTrue();
        } catch (DataAccessException ex) {
            LOG.warn("Unable to load marketplace scripts from database", ex);
            return applyHearts(entries, sessionUsername);
        }

        final String queryFilter = normalized;
        if (queryFilter != null) {
            publicScripts = publicScripts.stream()
                .filter(script -> {
                    String name = Optional.ofNullable(script.getName()).orElse("");
                    String desc = Optional.ofNullable(script.getDescription()).orElse("");
                    return StringNormalizer.toLowerCaseRoot(name).contains(queryFilter)
                        || StringNormalizer.toLowerCaseRoot(desc).contains(queryFilter);
                })
                .toList();
        }

        Map<String, Asset> assets = assetRepository
            .findAllById(publicScripts.stream().map(ScriptAsset::getId).toList())
            .stream()
            .collect(Collectors.toMap(Asset::getId, a -> a));

        entries.addAll(
            publicScripts.stream()
                .map(script -> toEntry(script, assets.get(script.getId())))
                .toList()
        );

        return applyHearts(entries, sessionUsername);
    }

    @Transactional
    public Optional<ScriptMarketplaceEntry> toggleHeart(String scriptId, String sessionUsername) {
        if (scriptId == null || scriptId.isBlank() || sessionUsername == null || sessionUsername.isBlank()) {
            return Optional.empty();
        }
        try {
            if (heartRepository.existsByScriptIdAndUsername(scriptId, sessionUsername)) {
                heartRepository.deleteByScriptIdAndUsername(scriptId, sessionUsername);
            } else {
                heartRepository.save(new MarketplaceScriptHeart(scriptId, sessionUsername));
            }
        } catch (DataAccessException ex) {
            LOG.warn("Unable to update marketplace heart for {}", scriptId, ex);
            return Optional.empty();
        }
        return loadEntryWithHearts(scriptId, sessionUsername);
    }

    public Optional<AssetContent> getLogo(String scriptId) {
        Optional<MarketplaceScriptSeedLoader.SeedScript> seed = seedLoader.findById(scriptId);
        if (seed.isPresent()) {
            return seed.get().loadLogo();
        }
        try {
            return scriptAssetRepository.findById(scriptId)
                .filter(ScriptAsset::isPublic)
                .map(ScriptAsset::getLogoFileId)
                .flatMap(scriptAssetFileRepository::findById)
                .flatMap(file -> assetStorageService.loadAssetFileSafely(
                    file.getBroadcaster(), file.getId(), file.getMediaType()));
        } catch (DataAccessException ex) {
            LOG.warn("Unable to load marketplace logo for script {}", scriptId, ex);
            return Optional.empty();
        }
    }

    // ---- helpers ----

    private Optional<ScriptMarketplaceEntry> loadEntryWithHearts(String scriptId, String sessionUsername) {
        Optional<MarketplaceScriptSeedLoader.SeedScript> seed = seedLoader.findById(scriptId);
        if (seed.isPresent()) {
            return Optional.of(applyHearts(seed.get().entry(), sessionUsername));
        }
        ScriptAsset script;
        try {
            script = scriptAssetRepository.findById(scriptId).filter(ScriptAsset::isPublic).orElse(null);
        } catch (DataAccessException ex) {
            LOG.warn("Unable to load marketplace script {}", scriptId, ex);
            return Optional.empty();
        }
        if (script == null) {
            return Optional.empty();
        }
        Asset asset = assetRepository.findById(scriptId).orElse(null);
        return Optional.of(applyHearts(toEntry(script, asset), sessionUsername));
    }

    private ScriptMarketplaceEntry toEntry(ScriptAsset script, Asset asset) {
        String broadcaster = asset != null ? asset.getBroadcaster() : "";
        String logoUrl = script.getLogoFileId() == null
            ? null
            : LOGO_URL_PREFIX + script.getId() + "/logo";
        return new ScriptMarketplaceEntry(
            script.getId(),
            script.getName(),
            script.getDescription(),
            logoUrl,
            broadcaster,
            AllowedDomainNormalizer.normalizeLenient(script.getAllowedDomains()),
            0,
            false
        );
    }

    private ScriptMarketplaceEntry applyHearts(ScriptMarketplaceEntry entry, String sessionUsername) {
        if (entry == null || entry.id() == null) {
            return entry;
        }
        long count;
        boolean hearted;
        try {
            count = heartRepository.countByScriptId(entry.id());
            hearted = sessionUsername != null
                && heartRepository.existsByScriptIdAndUsername(entry.id(), sessionUsername);
        } catch (DataAccessException ex) {
            LOG.warn("Unable to load heart summary for script {}", entry.id(), ex);
            count = 0;
            hearted = false;
        }
        return withHearts(entry, count, hearted);
    }

    private List<ScriptMarketplaceEntry> applyHearts(List<ScriptMarketplaceEntry> entries, String sessionUsername) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        List<String> ids = entries.stream().map(ScriptMarketplaceEntry::id).filter(Objects::nonNull).toList();
        Map<String, Long> counts = new HashMap<>();
        Set<String> heartedIds = new HashSet<>();
        try {
            if (!ids.isEmpty()) {
                counts.putAll(
                    heartRepository.countByScriptIds(ids).stream()
                        .collect(Collectors.toMap(
                            MarketplaceScriptHeartRepository.ScriptHeartCount::getScriptId,
                            MarketplaceScriptHeartRepository.ScriptHeartCount::getHeartCount
                        ))
                );
                if (sessionUsername != null && !sessionUsername.isBlank()) {
                    heartedIds.addAll(
                        heartRepository.findByUsernameAndScriptIdIn(sessionUsername, ids).stream()
                            .map(MarketplaceScriptHeart::getScriptId)
                            .collect(Collectors.toSet())
                    );
                }
            }
        } catch (DataAccessException ex) {
            LOG.warn("Unable to load bulk heart summaries", ex);
        }

        Comparator<ScriptMarketplaceEntry> byHeartsThenName = Comparator
            .comparingLong((ScriptMarketplaceEntry e) -> counts.getOrDefault(e.id(), 0L))
            .reversed()
            .thenComparing(ScriptMarketplaceEntry::name, Comparator.nullsLast(String::compareToIgnoreCase));

        return entries.stream()
            .map(e -> withHearts(e, counts.getOrDefault(e.id(), 0L), heartedIds.contains(e.id())))
            .sorted(byHeartsThenName)
            .toList();
    }

    private static ScriptMarketplaceEntry withHearts(ScriptMarketplaceEntry e, long count, boolean hearted) {
        return new ScriptMarketplaceEntry(
            e.id(), e.name(), e.description(), e.logoUrl(), e.broadcaster(),
            e.allowedDomains(), count, hearted
        );
    }
}
