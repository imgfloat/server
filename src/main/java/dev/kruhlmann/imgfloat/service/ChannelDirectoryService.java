package dev.kruhlmann.imgfloat.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE;

import dev.kruhlmann.imgfloat.model.Asset;
import dev.kruhlmann.imgfloat.model.AssetEvent;
import dev.kruhlmann.imgfloat.model.AssetPatch;
import dev.kruhlmann.imgfloat.model.AssetType;
import dev.kruhlmann.imgfloat.model.AssetView;
import dev.kruhlmann.imgfloat.model.AudioAsset;
import dev.kruhlmann.imgfloat.model.CanvasEvent;
import dev.kruhlmann.imgfloat.model.CanvasSettingsRequest;
import dev.kruhlmann.imgfloat.model.Channel;
import dev.kruhlmann.imgfloat.model.CodeAssetRequest;
import dev.kruhlmann.imgfloat.model.PlaybackRequest;
import dev.kruhlmann.imgfloat.model.ScriptAsset;
import dev.kruhlmann.imgfloat.model.ScriptAssetAttachment;
import dev.kruhlmann.imgfloat.model.ScriptAssetAttachmentView;
import dev.kruhlmann.imgfloat.model.ScriptAssetFile;
import dev.kruhlmann.imgfloat.model.ScriptMarketplaceEntry;
import dev.kruhlmann.imgfloat.model.Settings;
import dev.kruhlmann.imgfloat.model.TransformRequest;
import dev.kruhlmann.imgfloat.model.VisibilityRequest;
import dev.kruhlmann.imgfloat.model.VisualAsset;
import dev.kruhlmann.imgfloat.repository.AssetRepository;
import dev.kruhlmann.imgfloat.repository.AudioAssetRepository;
import dev.kruhlmann.imgfloat.repository.ChannelRepository;
import dev.kruhlmann.imgfloat.repository.ScriptAssetRepository;
import dev.kruhlmann.imgfloat.repository.ScriptAssetAttachmentRepository;
import dev.kruhlmann.imgfloat.repository.ScriptAssetFileRepository;
import dev.kruhlmann.imgfloat.repository.VisualAssetRepository;
import dev.kruhlmann.imgfloat.service.media.AssetContent;
import dev.kruhlmann.imgfloat.service.media.MediaDetectionService;
import dev.kruhlmann.imgfloat.service.media.MediaOptimizationService;
import dev.kruhlmann.imgfloat.service.media.OptimizedAsset;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChannelDirectoryService {

    private static final Logger logger = LoggerFactory.getLogger(ChannelDirectoryService.class);
    private static final Pattern SAFE_FILENAME = Pattern.compile("[^a-zA-Z0-9._ -]");
    private static final String DEFAULT_CODE_MEDIA_TYPE = "application/javascript";

    private final ChannelRepository channelRepository;
    private final AssetRepository assetRepository;
    private final VisualAssetRepository visualAssetRepository;
    private final AudioAssetRepository audioAssetRepository;
    private final ScriptAssetRepository scriptAssetRepository;
    private final ScriptAssetAttachmentRepository scriptAssetAttachmentRepository;
    private final ScriptAssetFileRepository scriptAssetFileRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final AssetStorageService assetStorageService;
    private final MediaDetectionService mediaDetectionService;
    private final MediaOptimizationService mediaOptimizationService;
    private final SettingsService settingsService;
    private final long uploadLimitBytes;
    private final MarketplaceScriptSeedLoader marketplaceScriptSeedLoader;

    @Autowired
    public ChannelDirectoryService(
        ChannelRepository channelRepository,
        AssetRepository assetRepository,
        VisualAssetRepository visualAssetRepository,
        AudioAssetRepository audioAssetRepository,
        ScriptAssetRepository scriptAssetRepository,
        ScriptAssetAttachmentRepository scriptAssetAttachmentRepository,
        ScriptAssetFileRepository scriptAssetFileRepository,
        SimpMessagingTemplate messagingTemplate,
        AssetStorageService assetStorageService,
        MediaDetectionService mediaDetectionService,
        MediaOptimizationService mediaOptimizationService,
        SettingsService settingsService,
        long uploadLimitBytes,
        MarketplaceScriptSeedLoader marketplaceScriptSeedLoader
    ) {
        this.channelRepository = channelRepository;
        this.assetRepository = assetRepository;
        this.visualAssetRepository = visualAssetRepository;
        this.audioAssetRepository = audioAssetRepository;
        this.scriptAssetRepository = scriptAssetRepository;
        this.scriptAssetAttachmentRepository = scriptAssetAttachmentRepository;
        this.scriptAssetFileRepository = scriptAssetFileRepository;
        this.messagingTemplate = messagingTemplate;
        this.assetStorageService = assetStorageService;
        this.mediaDetectionService = mediaDetectionService;
        this.mediaOptimizationService = mediaOptimizationService;
        this.settingsService = settingsService;
        this.uploadLimitBytes = uploadLimitBytes;
        this.marketplaceScriptSeedLoader = marketplaceScriptSeedLoader;
    }

    public Channel getOrCreateChannel(String broadcaster) {
        String normalized = normalize(broadcaster);
        return channelRepository.findById(normalized).orElseGet(() -> channelRepository.save(new Channel(normalized)));
    }

    public List<String> searchBroadcasters(String query) {
        String q = normalize(query);
        return channelRepository
            .findTop50ByBroadcasterContainingIgnoreCaseOrderByBroadcasterAsc(q == null ? "" : q)
            .stream()
            .map(Channel::getBroadcaster)
            .toList();
    }

    public boolean addAdmin(String broadcaster, String username) {
        Channel channel = getOrCreateChannel(broadcaster);
        boolean added = channel.addAdmin(username);
        if (added) {
            channelRepository.saveAndFlush(channel);
            messagingTemplate.convertAndSend(topicFor(broadcaster), "Admin added: " + username);
        }
        return added;
    }

    public boolean removeAdmin(String broadcaster, String username) {
        Channel channel = getOrCreateChannel(broadcaster);
        boolean removed = channel.removeAdmin(username);
        if (removed) {
            channelRepository.saveAndFlush(channel);
            messagingTemplate.convertAndSend(topicFor(broadcaster), "Admin removed: " + username);
        }
        return removed;
    }

    public Collection<AssetView> getAssetsForAdmin(String broadcaster) {
        String normalized = normalize(broadcaster);
        return sortAndMapAssets(normalized, assetRepository.findByBroadcaster(normalized));
    }

    public Collection<AssetView> getVisibleAssets(String broadcaster) {
        String normalized = normalize(broadcaster);
        List<Asset> assets = assetRepository.findByBroadcaster(normalized);
        List<String> visualIds = assets
            .stream()
            .filter(
                (asset) ->
                    asset.getAssetType() == AssetType.IMAGE ||
                    asset.getAssetType() == AssetType.VIDEO ||
                    asset.getAssetType() == AssetType.OTHER
            )
            .map(Asset::getId)
            .toList();
        Map<String, Asset> assetById = assets.stream().collect(Collectors.toMap(Asset::getId, (asset) -> asset));
        return visualAssetRepository
            .findByIdInAndHiddenFalse(visualIds)
            .stream()
            .map((visual) -> {
                Asset asset = assetById.get(visual.getId());
                return asset == null ? null : AssetView.fromVisual(normalized, asset, visual);
            })
            .filter(Objects::nonNull)
            .sorted(Comparator.comparingInt(AssetView::zIndex))
            .toList();
    }

    public CanvasSettingsRequest getCanvasSettings(String broadcaster) {
        Channel channel = getOrCreateChannel(broadcaster);
        return new CanvasSettingsRequest(channel.getCanvasWidth(), channel.getCanvasHeight());
    }

    public CanvasSettingsRequest updateCanvasSettings(String broadcaster, CanvasSettingsRequest req) {
        Channel channel = getOrCreateChannel(broadcaster);
        channel.setCanvasWidth(req.getWidth());
        channel.setCanvasHeight(req.getHeight());
        channelRepository.save(channel);
        CanvasSettingsRequest response = new CanvasSettingsRequest(channel.getCanvasWidth(), channel.getCanvasHeight());
        messagingTemplate.convertAndSend(topicFor(broadcaster), CanvasEvent.updated(broadcaster, response));
        return response;
    }

    public Optional<AssetView> createAsset(String broadcaster, MultipartFile file) throws IOException {
        long fileSize = file.getSize();
        if (fileSize > uploadLimitBytes) {
            throw new ResponseStatusException(
                PAYLOAD_TOO_LARGE,
                String.format(
                    "Uploaded file is too large (%d bytes). Maximum allowed is %d bytes.",
                    fileSize,
                    uploadLimitBytes
                )
            );
        }
        Channel channel = getOrCreateChannel(broadcaster);
        byte[] bytes = file.getBytes();
        String mediaType = mediaDetectionService
            .detectAllowedMediaType(file, bytes)
            .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Unsupported media type"));

        OptimizedAsset optimized = mediaOptimizationService.optimizeAsset(bytes, mediaType);
        if (optimized == null) {
            return Optional.empty();
        }

        String safeName = Optional.ofNullable(file.getOriginalFilename())
            .map(this::sanitizeFilename)
            .filter((s) -> !s.isBlank())
            .orElse("asset_" + System.currentTimeMillis());

        boolean isAudio = optimized.mediaType().startsWith("audio/");
        boolean isCode = isCodeMediaType(optimized.mediaType()) || isCodeMediaType(mediaType);
        AssetType assetType = AssetType.fromMediaType(optimized.mediaType(), mediaType);
        Asset asset = new Asset(channel.getBroadcaster(), assetType);

        assetStorageService.storeAsset(
            channel.getBroadcaster(),
            asset.getId(),
            optimized.bytes(),
            optimized.mediaType()
        );

        AssetView view;
        asset = assetRepository.save(asset);

        if (isAudio) {
            AudioAsset audio = new AudioAsset(asset.getId(), safeName);
            audio.setMediaType(optimized.mediaType());
            audio.setOriginalMediaType(mediaType);
            audioAssetRepository.save(audio);
            view = AssetView.fromAudio(channel.getBroadcaster(), asset, audio);
        } else if (isCode) {
            ScriptAsset script = new ScriptAsset(asset.getId(), safeName);
            script.setMediaType(optimized.mediaType());
            script.setOriginalMediaType(mediaType);
            script.setSourceFileId(asset.getId());
            script.setAttachments(List.of());
            scriptAssetRepository.save(script);
            ScriptAssetFile sourceFile = new ScriptAssetFile(asset.getBroadcaster(), AssetType.SCRIPT);
            sourceFile.setId(asset.getId());
            sourceFile.setMediaType(optimized.mediaType());
            sourceFile.setOriginalMediaType(mediaType);
            scriptAssetFileRepository.save(sourceFile);
            view = AssetView.fromScript(channel.getBroadcaster(), asset, script);
        } else {
            double defaultWidth = 640;
            double defaultHeight = 360;
            double width = optimized.width() > 0 ? optimized.width() : defaultWidth;
            double height = optimized.height() > 0 ? optimized.height() : defaultHeight;
            VisualAsset visual = new VisualAsset(asset.getId(), safeName, width, height);
            visual.setOriginalMediaType(mediaType);
            visual.setMediaType(optimized.mediaType());
            visual.setMuted(optimized.mediaType().startsWith("video/"));
            visual.setZIndex(nextZIndex(channel.getBroadcaster()));
            assetStorageService.storePreview(channel.getBroadcaster(), asset.getId(), optimized.previewBytes());
            visual.setPreview(optimized.previewBytes() != null ? asset.getId() + ".png" : "");
            visualAssetRepository.save(visual);
            view = AssetView.fromVisual(channel.getBroadcaster(), asset, visual);
        }

        messagingTemplate.convertAndSend(topicFor(broadcaster), AssetEvent.created(broadcaster, view));

        return Optional.of(view);
    }

    public Optional<AssetView> createCodeAsset(String broadcaster, CodeAssetRequest request) {
        validateCodeAssetSource(request.getSource());
        Channel channel = getOrCreateChannel(broadcaster);
        byte[] bytes = request.getSource().getBytes(StandardCharsets.UTF_8);
        enforceUploadLimit(bytes.length);

        Asset asset = new Asset(channel.getBroadcaster(), AssetType.SCRIPT);
        ScriptAssetFile sourceFile = new ScriptAssetFile(asset.getBroadcaster(), AssetType.SCRIPT);
        sourceFile.setId(asset.getId());
        sourceFile.setMediaType(DEFAULT_CODE_MEDIA_TYPE);
        sourceFile.setOriginalMediaType(DEFAULT_CODE_MEDIA_TYPE);

        try {
            assetStorageService.storeAsset(
                sourceFile.getBroadcaster(),
                sourceFile.getId(),
                bytes,
                DEFAULT_CODE_MEDIA_TYPE
            );
        } catch (IOException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Unable to store custom script", e);
        }

        asset = assetRepository.save(asset);
        scriptAssetFileRepository.save(sourceFile);
        ScriptAsset script = new ScriptAsset(asset.getId(), request.getName().trim());
        script.setOriginalMediaType(DEFAULT_CODE_MEDIA_TYPE);
        script.setMediaType(DEFAULT_CODE_MEDIA_TYPE);
        script.setSourceFileId(sourceFile.getId());
        script.setDescription(normalizeDescription(request.getDescription()));
        script.setPublic(Boolean.TRUE.equals(request.getIsPublic()));
        script.setAttachments(List.of());
        scriptAssetRepository.save(script);
        AssetView view = AssetView.fromScript(channel.getBroadcaster(), asset, script);
        messagingTemplate.convertAndSend(topicFor(broadcaster), AssetEvent.created(broadcaster, view));
        return Optional.of(view);
    }

    public Optional<AssetView> updateCodeAsset(String broadcaster, String assetId, CodeAssetRequest request) {
        validateCodeAssetSource(request.getSource());
        String normalized = normalize(broadcaster);
        byte[] bytes = request.getSource().getBytes(StandardCharsets.UTF_8);
        enforceUploadLimit(bytes.length);

        return assetRepository
            .findById(assetId)
            .filter((asset) -> normalized.equals(asset.getBroadcaster()))
            .map((asset) -> {
                if (asset.getAssetType() != AssetType.SCRIPT) {
                    throw new ResponseStatusException(BAD_REQUEST, "Asset is not a script");
                }
                ScriptAsset script = scriptAssetRepository
                    .findById(asset.getId())
                    .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Asset is not a script"));
                String sourceFileId = script.getSourceFileId();
                if (sourceFileId == null || sourceFileId.isBlank()) {
                    sourceFileId = asset.getId();
                    script.setSourceFileId(sourceFileId);
                }
                String resolvedSourceFileId = sourceFileId;
                ScriptAssetFile sourceFile = scriptAssetFileRepository
                    .findById(resolvedSourceFileId)
                    .orElseGet(() -> {
                        ScriptAssetFile file = new ScriptAssetFile(asset.getBroadcaster(), AssetType.SCRIPT);
                        file.setId(resolvedSourceFileId);
                        file.setMediaType(DEFAULT_CODE_MEDIA_TYPE);
                        file.setOriginalMediaType(DEFAULT_CODE_MEDIA_TYPE);
                        return scriptAssetFileRepository.save(file);
                    });
                script.setName(request.getName().trim());
                if (request.getDescription() != null) {
                    script.setDescription(normalizeDescription(request.getDescription()));
                }
                if (request.getIsPublic() != null) {
                    script.setPublic(request.getIsPublic());
                }
                script.setOriginalMediaType(DEFAULT_CODE_MEDIA_TYPE);
                script.setMediaType(DEFAULT_CODE_MEDIA_TYPE);
                script.setAttachments(loadScriptAttachments(normalized, asset.getId(), null));
                try {
                    assetStorageService.storeAsset(
                        sourceFile.getBroadcaster(),
                        sourceFile.getId(),
                        bytes,
                        DEFAULT_CODE_MEDIA_TYPE
                    );
                } catch (IOException e) {
                    throw new ResponseStatusException(BAD_REQUEST, "Unable to store custom script", e);
                }
                assetRepository.save(asset);
                scriptAssetRepository.save(script);
                AssetView view = AssetView.fromScript(normalized, asset, script);
                messagingTemplate.convertAndSend(topicFor(broadcaster), AssetEvent.updated(broadcaster, view));
                return view;
            });
    }

    public Optional<AssetView> updateScriptLogo(String broadcaster, String assetId, MultipartFile file)
        throws IOException {
        Asset asset = requireScriptAssetForBroadcaster(broadcaster, assetId);
        byte[] bytes = file.getBytes();
        enforceUploadLimit(bytes.length);
        String mediaType = mediaDetectionService
            .detectAllowedMediaType(file, bytes)
            .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Unsupported media type"));
        OptimizedAsset optimized = mediaOptimizationService.optimizeAsset(bytes, mediaType);
        if (optimized == null) {
            return Optional.empty();
        }
        AssetType assetType = AssetType.fromMediaType(optimized.mediaType(), mediaType);
        if (assetType != AssetType.IMAGE) {
            throw new ResponseStatusException(BAD_REQUEST, "Logo must be an image.");
        }
        ScriptAsset script = scriptAssetRepository
            .findById(asset.getId())
            .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Asset is not a script"));

        String previousLogoFileId = script.getLogoFileId();
        ScriptAssetFile logoFile = new ScriptAssetFile(asset.getBroadcaster(), AssetType.IMAGE);
        logoFile.setMediaType(optimized.mediaType());
        logoFile.setOriginalMediaType(mediaType);
        scriptAssetFileRepository.save(logoFile);

        assetStorageService.storeAsset(
            logoFile.getBroadcaster(),
            logoFile.getId(),
            optimized.bytes(),
            optimized.mediaType()
        );

        script.setLogoFileId(logoFile.getId());
        script.setAttachments(loadScriptAttachments(asset.getBroadcaster(), asset.getId(), null));
        scriptAssetRepository.save(script);

        removeScriptAssetFileIfOrphaned(previousLogoFileId);

        AssetView view = AssetView.fromScript(asset.getBroadcaster(), asset, script);
        messagingTemplate.convertAndSend(topicFor(broadcaster), AssetEvent.updated(broadcaster, view));
        return Optional.of(view);
    }

    public Optional<AssetView> clearScriptLogo(String broadcaster, String assetId) {
        Asset asset = requireScriptAssetForBroadcaster(broadcaster, assetId);
        ScriptAsset script = scriptAssetRepository
            .findById(asset.getId())
            .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Asset is not a script"));
        String previousLogoFileId = script.getLogoFileId();
        if (previousLogoFileId == null) {
            return Optional.empty();
        }
        script.setLogoFileId(null);
        script.setAttachments(loadScriptAttachments(asset.getBroadcaster(), asset.getId(), null));
        scriptAssetRepository.save(script);
        removeScriptAssetFileIfOrphaned(previousLogoFileId);
        AssetView view = AssetView.fromScript(asset.getBroadcaster(), asset, script);
        messagingTemplate.convertAndSend(topicFor(broadcaster), AssetEvent.updated(broadcaster, view));
        return Optional.of(view);
    }

    public List<ScriptMarketplaceEntry> listMarketplaceScripts(String query) {
        String q = normalizeDescription(query);
        String normalizedQuery = q == null ? null : q.toLowerCase(Locale.ROOT);
        List<ScriptMarketplaceEntry> entries = new ArrayList<>(marketplaceScriptSeedLoader.listEntriesForQuery(normalizedQuery));
        List<ScriptAsset> scripts;
        try {
            scripts = scriptAssetRepository.findByIsPublicTrue();
        } catch (DataAccessException ex) {
            logger.warn("Unable to load marketplace scripts", ex);
            return entries
                .stream()
                .sorted(
                    Comparator.comparing(ScriptMarketplaceEntry::name, Comparator.nullsLast(String::compareToIgnoreCase))
                )
                .toList();
        }
        if (normalizedQuery != null && !normalizedQuery.isBlank()) {
            scripts =
                scripts
                    .stream()
                    .filter((script) -> {
                        String name = Optional.ofNullable(script.getName()).orElse("");
                        String description = Optional.ofNullable(script.getDescription()).orElse("");
                        return name.toLowerCase(Locale.ROOT).contains(normalizedQuery) ||
                        description.toLowerCase(Locale.ROOT).contains(normalizedQuery);
                    })
                    .toList();
        }
        Map<String, Asset> assets = assetRepository
            .findAllById(scripts.stream().map(ScriptAsset::getId).toList())
            .stream()
            .collect(Collectors.toMap(Asset::getId, (asset) -> asset));

        entries.addAll(
            scripts
                .stream()
                .map((script) -> {
                    Asset asset = assets.get(script.getId());
                    String broadcaster = asset != null ? asset.getBroadcaster() : "";
                    String logoUrl = script.getLogoFileId() == null
                        ? null
                        : "/api/marketplace/scripts/" + script.getId() + "/logo";
                    return new ScriptMarketplaceEntry(
                        script.getId(),
                        script.getName(),
                        script.getDescription(),
                        logoUrl,
                        broadcaster
                    );
                })
                .toList()
        );

        return entries
            .stream()
            .sorted(Comparator.comparing(ScriptMarketplaceEntry::name, Comparator.nullsLast(String::compareToIgnoreCase)))
            .toList();
    }

    public Optional<AssetContent> getMarketplaceLogo(String scriptId) {
        Optional<MarketplaceScriptSeedLoader.SeedScript> seedScript = marketplaceScriptSeedLoader.findById(scriptId);
        if (seedScript.isPresent()) {
            return seedScript.get().loadLogo();
        }
        try {
            return scriptAssetRepository
                .findById(scriptId)
                .filter(ScriptAsset::isPublic)
                .map(ScriptAsset::getLogoFileId)
                .flatMap((logoFileId) -> scriptAssetFileRepository.findById(logoFileId))
                .flatMap((file) ->
                    assetStorageService.loadAssetFileSafely(file.getBroadcaster(), file.getId(), file.getMediaType())
                );
        } catch (DataAccessException ex) {
            logger.warn("Unable to load marketplace logo", ex);
            return Optional.empty();
        }
    }

    public Optional<AssetView> importMarketplaceScript(String targetBroadcaster, String scriptId) {
        Optional<MarketplaceScriptSeedLoader.SeedScript> seedScript = marketplaceScriptSeedLoader.findById(scriptId);
        if (seedScript.isPresent()) {
            return importSeedMarketplaceScript(targetBroadcaster, seedScript.get());
        }
        ScriptAsset sourceScript;
        try {
            sourceScript = scriptAssetRepository.findById(scriptId).filter(ScriptAsset::isPublic).orElse(null);
        } catch (DataAccessException ex) {
            logger.warn("Unable to import marketplace script {}", scriptId, ex);
            return Optional.empty();
        }
        Asset sourceAsset = sourceScript == null ? null : assetRepository.findById(scriptId).orElse(null);
        if (sourceScript == null || sourceAsset == null) {
            return Optional.empty();
        }
        AssetContent sourceContent = loadScriptSourceContent(sourceAsset, sourceScript).orElse(null);
        if (sourceContent == null) {
            return Optional.empty();
        }

        Asset asset = new Asset(targetBroadcaster, AssetType.SCRIPT);
        ScriptAssetFile sourceFile = new ScriptAssetFile(asset.getBroadcaster(), AssetType.SCRIPT);
        sourceFile.setId(asset.getId());
        sourceFile.setMediaType(sourceContent.mediaType());
        sourceFile.setOriginalMediaType(sourceContent.mediaType());
        try {
            assetStorageService.storeAsset(
                sourceFile.getBroadcaster(),
                sourceFile.getId(),
                sourceContent.bytes(),
                sourceContent.mediaType()
            );
        } catch (IOException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Unable to store custom script", e);
        }
        assetRepository.save(asset);
        scriptAssetFileRepository.save(sourceFile);

        ScriptAsset script = new ScriptAsset(asset.getId(), sourceScript.getName());
        script.setDescription(sourceScript.getDescription());
        script.setPublic(false);
        script.setMediaType(sourceContent.mediaType());
        script.setOriginalMediaType(sourceContent.mediaType());
        script.setSourceFileId(sourceFile.getId());
        script.setLogoFileId(sourceScript.getLogoFileId());
        script.setAttachments(List.of());
        scriptAssetRepository.save(script);

        List<ScriptAssetAttachment> sourceAttachments = scriptAssetAttachmentRepository
            .findByScriptAssetId(sourceScript.getId());
        List<ScriptAssetAttachment> newAttachments = sourceAttachments
            .stream()
            .map((attachment) -> {
                ScriptAssetAttachment copy = new ScriptAssetAttachment(asset.getId(), attachment.getName());
                String fileId = attachment.getFileId() != null ? attachment.getFileId() : attachment.getId();
                copy.setFileId(fileId);
                copy.setMediaType(attachment.getMediaType());
                copy.setOriginalMediaType(attachment.getOriginalMediaType());
                copy.setAssetType(attachment.getAssetType());
                return copy;
            })
            .toList();
        if (!newAttachments.isEmpty()) {
            scriptAssetAttachmentRepository.saveAll(newAttachments);
        }
        script.setAttachments(loadScriptAttachments(asset.getBroadcaster(), asset.getId(), null));
        AssetView view = AssetView.fromScript(asset.getBroadcaster(), asset, script);
        messagingTemplate.convertAndSend(topicFor(targetBroadcaster), AssetEvent.created(targetBroadcaster, view));
        return Optional.of(view);
    }

    private Optional<AssetView> importSeedMarketplaceScript(
        String targetBroadcaster,
        MarketplaceScriptSeedLoader.SeedScript seedScript
    ) {
        AssetContent sourceContent = seedScript.loadSource().orElse(null);
        if (sourceContent == null) {
            return Optional.empty();
        }

        Asset asset = new Asset(targetBroadcaster, AssetType.SCRIPT);
        ScriptAssetFile sourceFile = new ScriptAssetFile(asset.getBroadcaster(), AssetType.SCRIPT);
        sourceFile.setId(asset.getId());
        sourceFile.setMediaType(sourceContent.mediaType());
        sourceFile.setOriginalMediaType(sourceContent.mediaType());
        try {
            assetStorageService.storeAsset(
                sourceFile.getBroadcaster(),
                sourceFile.getId(),
                sourceContent.bytes(),
                sourceContent.mediaType()
            );
        } catch (IOException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Unable to store custom script", e);
        }
        assetRepository.save(asset);
        scriptAssetFileRepository.save(sourceFile);

        ScriptAsset script = new ScriptAsset(asset.getId(), seedScript.name());
        script.setDescription(seedScript.description());
        script.setPublic(false);
        script.setMediaType(sourceContent.mediaType());
        script.setOriginalMediaType(sourceContent.mediaType());
        script.setSourceFileId(sourceFile.getId());
        script.setAttachments(List.of());
        scriptAssetRepository.save(script);

        String logoFileId = seedScript
            .loadLogo()
            .map((logoContent) -> storeScriptAttachmentFile(asset, logoContent))
            .orElse(null);

        List<ScriptAssetAttachment> attachments = new ArrayList<>();
        if (logoFileId != null) {
            script.setLogoFileId(logoFileId);
        }
        for (MarketplaceScriptSeedLoader.SeedAttachment attachment : seedScript.attachments()) {
            AssetContent attachmentContent = loadSeedAttachment(attachment).orElse(null);
            if (attachmentContent == null) {
                continue;
            }
            String attachmentFileId = storeScriptAttachmentFile(asset, attachmentContent);
            ScriptAssetAttachment scriptAttachment = new ScriptAssetAttachment(asset.getId(), attachment.name());
            scriptAttachment.setFileId(attachmentFileId);
            scriptAttachment.setMediaType(attachmentContent.mediaType());
            scriptAttachment.setOriginalMediaType(attachmentContent.mediaType());
            scriptAttachment.setAssetType(AssetType.IMAGE);
            attachments.add(scriptAttachment);
        }
        if (!attachments.isEmpty()) {
            scriptAssetAttachmentRepository.saveAll(attachments);
        }

        script.setAttachments(loadScriptAttachments(asset.getBroadcaster(), asset.getId(), null));
        scriptAssetRepository.save(script);
        AssetView view = AssetView.fromScript(asset.getBroadcaster(), asset, script);
        messagingTemplate.convertAndSend(topicFor(targetBroadcaster), AssetEvent.created(targetBroadcaster, view));
        return Optional.of(view);
    }

    private Optional<AssetContent> loadSeedAttachment(MarketplaceScriptSeedLoader.SeedAttachment attachment) {
        AtomicReference<byte[]> cache = attachment.bytes();
        byte[] bytes = cache.get();
        if (bytes == null) {
            bytes = readSeedAttachment(attachment.path()).orElse(null);
            if (bytes != null) {
                cache.set(bytes);
            }
        }
        if (bytes == null) {
            return Optional.empty();
        }
        return Optional.of(new AssetContent(bytes, attachment.mediaType()));
    }

    private Optional<byte[]> readSeedAttachment(Path path) {
        try {
            return Optional.of(Files.readAllBytes(path));
        } catch (IOException ex) {
            logger.warn("Failed to read marketplace attachment {}", path, ex);
            return Optional.empty();
        }
    }

    private String storeScriptAttachmentFile(Asset asset, AssetContent attachmentContent) {
        ScriptAssetFile attachmentFile = new ScriptAssetFile(asset.getBroadcaster(), AssetType.IMAGE);
        attachmentFile.setMediaType(attachmentContent.mediaType());
        attachmentFile.setOriginalMediaType(attachmentContent.mediaType());
        try {
            assetStorageService.storeAsset(
                attachmentFile.getBroadcaster(),
                attachmentFile.getId(),
                attachmentContent.bytes(),
                attachmentContent.mediaType()
            );
        } catch (IOException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Unable to store script attachment", e);
        }
        scriptAssetFileRepository.save(attachmentFile);
        return attachmentFile.getId();
    }

    private String sanitizeFilename(String original) {
        String stripped = original.replaceAll("^.*[/\\\\]", "");
        return SAFE_FILENAME.matcher(stripped).replaceAll("_");
    }

    public Optional<AssetView> updateTransform(String broadcaster, String assetId, TransformRequest req) {
        String normalized = normalize(broadcaster);

        return assetRepository
            .findById(assetId)
            .filter((asset) -> normalized.equals(asset.getBroadcaster()))
            .map((asset) -> {
                if (asset.getAssetType() == AssetType.AUDIO) {
                    AudioAsset audio = audioAssetRepository
                        .findById(asset.getId())
                        .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Asset is not audio"));
                    AssetPatch.AudioSnapshot before = new AssetPatch.AudioSnapshot(
                        audio.isAudioLoop(),
                        audio.getAudioDelayMillis(),
                        audio.getAudioSpeed(),
                        audio.getAudioPitch(),
                        audio.getAudioVolume()
                    );
                    validateAudioTransform(req);
                    if (req.getAudioLoop() != null) audio.setAudioLoop(req.getAudioLoop());
                    if (req.getAudioDelayMillis() != null) audio.setAudioDelayMillis(req.getAudioDelayMillis());
                    if (req.getAudioSpeed() != null) audio.setAudioSpeed(req.getAudioSpeed());
                    if (req.getAudioPitch() != null) audio.setAudioPitch(req.getAudioPitch());
                    if (req.getAudioVolume() != null) audio.setAudioVolume(req.getAudioVolume());
                    audioAssetRepository.save(audio);
                    AssetView view = AssetView.fromAudio(normalized, asset, audio);
                    AssetPatch patch = AssetPatch.fromAudioTransform(before, audio, req);
                    if (hasPatchChanges(patch)) {
                        messagingTemplate.convertAndSend(topicFor(broadcaster), AssetEvent.updated(broadcaster, patch));
                    }
                    return view;
                }

                if (asset.getAssetType() == AssetType.SCRIPT) {
                    ScriptAsset script = scriptAssetRepository
                        .findById(asset.getId())
                        .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Asset is not a script"));
                    script.setAttachments(loadScriptAttachments(normalized, asset.getId(), null));
                    return AssetView.fromScript(normalized, asset, script);
                }

                VisualAsset visual = visualAssetRepository
                    .findById(asset.getId())
                    .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Asset is not visual"));
                AssetPatch.VisualSnapshot before = new AssetPatch.VisualSnapshot(
                    visual.getX(),
                    visual.getY(),
                    visual.getWidth(),
                    visual.getHeight(),
                    visual.getRotation(),
                    visual.getSpeed(),
                    visual.isMuted(),
                    visual.getZIndex(),
                    visual.getAudioVolume()
                );
                validateVisualTransform(req);

                if (req.getX() != null) visual.setX(req.getX());
                if (req.getY() != null) visual.setY(req.getY());
                if (req.getWidth() != null) visual.setWidth(req.getWidth());
                if (req.getHeight() != null) visual.setHeight(req.getHeight());
                if (req.getRotation() != null) visual.setRotation(req.getRotation());
                if (req.getZIndex() != null) visual.setZIndex(req.getZIndex());
                if (req.getSpeed() != null) visual.setSpeed(req.getSpeed());
                if (req.getMuted() != null) visual.setMuted(req.getMuted());
                if (req.getAudioVolume() != null) visual.setAudioVolume(req.getAudioVolume());

                visualAssetRepository.save(visual);

                AssetView view = AssetView.fromVisual(normalized, asset, visual);
                AssetPatch patch = AssetPatch.fromVisualTransform(before, visual, req);
                if (hasPatchChanges(patch)) {
                    messagingTemplate.convertAndSend(topicFor(broadcaster), AssetEvent.updated(broadcaster, patch));
                }
                return view;
            });
    }

    private void validateVisualTransform(TransformRequest req) {
        Settings settings = settingsService.get();
        double maxSpeed = settings.getMaxAssetPlaybackSpeedFraction();
        double minSpeed = settings.getMinAssetPlaybackSpeedFraction();
        double minVolume = settings.getMinAssetVolumeFraction();
        double maxVolume = settings.getMaxAssetVolumeFraction();
        int canvasMaxSizePixels = settings.getMaxCanvasSideLengthPixels();

        if (
            req.getWidth() == null || req.getWidth() <= 0 || req.getWidth() > canvasMaxSizePixels
        ) throw new ResponseStatusException(
            BAD_REQUEST,
            "Canvas width out of range [0 to " + canvasMaxSizePixels + "]"
        );
        if (
            req.getHeight() == null || req.getHeight() <= 0 || req.getHeight() > canvasMaxSizePixels
        ) throw new ResponseStatusException(
            BAD_REQUEST,
            "Canvas height out of range [0 to " + canvasMaxSizePixels + "]"
        );
        if (
            req.getSpeed() != null && (req.getSpeed() < minSpeed || req.getSpeed() > maxSpeed)
        ) throw new ResponseStatusException(BAD_REQUEST, "Speed out of range [" + minSpeed + " to " + maxSpeed + "]");
        if (req.getZIndex() != null && req.getZIndex() < 1) throw new ResponseStatusException(
            BAD_REQUEST,
            "zIndex must be >= 1"
        );
        if (
            req.getAudioVolume() != null && (req.getAudioVolume() < minVolume || req.getAudioVolume() > maxVolume)
        ) throw new ResponseStatusException(
            BAD_REQUEST,
            "Audio volume out of range [" + minVolume + " to " + maxVolume + "]"
        );
    }

    private void validateAudioTransform(TransformRequest req) {
        Settings settings = settingsService.get();
        double maxSpeed = settings.getMaxAssetPlaybackSpeedFraction();
        double minSpeed = settings.getMinAssetPlaybackSpeedFraction();
        double minPitch = settings.getMinAssetAudioPitchFraction();
        double maxPitch = settings.getMaxAssetAudioPitchFraction();
        double minVolume = settings.getMinAssetVolumeFraction();
        double maxVolume = settings.getMaxAssetVolumeFraction();

        if (req.getAudioDelayMillis() != null && req.getAudioDelayMillis() < 0) throw new ResponseStatusException(
            BAD_REQUEST,
            "Audio delay >= 0"
        );
        if (
            req.getAudioSpeed() != null && (req.getAudioSpeed() < minSpeed || req.getAudioSpeed() > maxSpeed)
        ) throw new ResponseStatusException(BAD_REQUEST, "Audio speed out of range");
        if (
            req.getAudioPitch() != null && (req.getAudioPitch() < minPitch || req.getAudioPitch() > maxPitch)
        ) throw new ResponseStatusException(BAD_REQUEST, "Audio pitch out of range");
        if (
            req.getAudioVolume() != null && (req.getAudioVolume() < minVolume || req.getAudioVolume() > maxVolume)
        ) throw new ResponseStatusException(
            BAD_REQUEST,
            "Audio volume out of range [" + minVolume + " to " + maxVolume + "]"
        );
    }

    public Optional<AssetView> triggerPlayback(String broadcaster, String assetId, PlaybackRequest req) {
        String normalized = normalize(broadcaster);
        return assetRepository
            .findById(assetId)
            .filter((a) -> normalized.equals(a.getBroadcaster()))
            .map((asset) -> {
                AssetView view = resolveAssetView(normalized, asset);
                if (view == null) {
                    throw new ResponseStatusException(BAD_REQUEST, "Asset data missing");
                }
                boolean play = req == null || req.getPlay();
                messagingTemplate.convertAndSend(topicFor(broadcaster), AssetEvent.play(broadcaster, view, play));
                return view;
            });
    }

    public Optional<AssetView> updateVisibility(String broadcaster, String assetId, VisibilityRequest request) {
        String normalized = normalize(broadcaster);
        return assetRepository
            .findById(assetId)
            .filter((a) -> normalized.equals(a.getBroadcaster()))
            .map((asset) -> {
                boolean hidden = request.isHidden();
                if (asset.getAssetType() == AssetType.AUDIO) {
                    AudioAsset audio = audioAssetRepository
                        .findById(asset.getId())
                        .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Asset is not audio"));
                    if (audio.isHidden() == hidden) {
                        return AssetView.fromAudio(normalized, asset, audio);
                    }
                    audio.setHidden(hidden);
                    audioAssetRepository.save(audio);
                    AssetView view = AssetView.fromAudio(normalized, asset, audio);
                    AssetPatch patch = AssetPatch.fromVisibility(asset.getId(), hidden);
                    AssetView payload = hidden ? null : view;
                    messagingTemplate.convertAndSend(
                        topicFor(broadcaster),
                        AssetEvent.visibility(broadcaster, patch, payload)
                    );
                    return view;
                }

                if (asset.getAssetType() == AssetType.SCRIPT) {
                    ScriptAsset script = scriptAssetRepository
                        .findById(asset.getId())
                        .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Asset is not a script"));
                    script.setAttachments(loadScriptAttachments(normalized, asset.getId(), null));
                    return AssetView.fromScript(normalized, asset, script);
                }

                VisualAsset visual = visualAssetRepository
                    .findById(asset.getId())
                    .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Asset is not visual"));
                if (visual.isHidden() == hidden) {
                    return AssetView.fromVisual(normalized, asset, visual);
                }
                visual.setHidden(hidden);
                visualAssetRepository.save(visual);
                AssetView view = AssetView.fromVisual(normalized, asset, visual);
                AssetPatch patch = AssetPatch.fromVisibility(asset.getId(), hidden);
                AssetView payload = hidden ? null : view;
                messagingTemplate.convertAndSend(
                    topicFor(broadcaster),
                    AssetEvent.visibility(broadcaster, patch, payload)
                );
                return view;
            });
    }

    @Transactional
    public boolean deleteAsset(String assetId) {
        return assetRepository
            .findById(assetId)
            .map((asset) -> {
                if (asset.getAssetType() == AssetType.SCRIPT) {
                    ScriptAsset script = scriptAssetRepository.findById(asset.getId()).orElse(null);
                    List<String> attachmentFileIds = scriptAssetAttachmentRepository
                        .findByScriptAssetId(asset.getId())
                        .stream()
                        .map((attachment) -> attachment.getFileId() != null ? attachment.getFileId() : attachment.getId())
                        .toList();
                    scriptAssetAttachmentRepository.deleteByScriptAssetId(asset.getId());
                    scriptAssetRepository.deleteById(asset.getId());
                    assetRepository.delete(asset);
                    if (script != null) {
                        removeScriptAssetFileIfOrphaned(script.getSourceFileId());
                        removeScriptAssetFileIfOrphaned(script.getLogoFileId());
                    }
                    attachmentFileIds.forEach(this::removeScriptAssetFileIfOrphaned);
                } else {
                    deleteAssetStorage(asset);
                    switch (asset.getAssetType()) {
                        case AUDIO -> audioAssetRepository.deleteById(asset.getId());
                        case SCRIPT -> scriptAssetRepository.deleteById(asset.getId());
                        default -> visualAssetRepository.deleteById(asset.getId());
                    }
                    assetRepository.delete(asset);
                }
                messagingTemplate.convertAndSend(
                    topicFor(asset.getBroadcaster()),
                    AssetEvent.deleted(asset.getBroadcaster(), assetId)
                );
                return true;
            })
            .orElse(false);
    }

    public Optional<AssetContent> getAssetContent(String assetId) {
        return assetRepository.findById(assetId).flatMap(this::loadAssetContent);
    }

    public List<ScriptAssetAttachmentView> listScriptAttachments(String broadcaster, String scriptAssetId) {
        Asset asset = requireScriptAssetForBroadcaster(broadcaster, scriptAssetId);
        return loadScriptAttachments(normalize(broadcaster), asset.getId(), null);
    }

    public Optional<ScriptAssetAttachmentView> createScriptAttachment(
        String broadcaster,
        String scriptAssetId,
        MultipartFile file
    ) throws IOException {
        long fileSize = file.getSize();
        if (fileSize > uploadLimitBytes) {
            throw new ResponseStatusException(
                PAYLOAD_TOO_LARGE,
                String.format(
                    "Uploaded file is too large (%d bytes). Maximum allowed is %d bytes.",
                    fileSize,
                    uploadLimitBytes
                )
            );
        }

        Asset asset = requireScriptAssetForBroadcaster(broadcaster, scriptAssetId);
        byte[] bytes = file.getBytes();
        String mediaType = mediaDetectionService
            .detectAllowedMediaType(file, bytes)
            .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Unsupported media type"));

        OptimizedAsset optimized = mediaOptimizationService.optimizeAsset(bytes, mediaType);
        if (optimized == null) {
            return Optional.empty();
        }

        AssetType assetType = AssetType.fromMediaType(optimized.mediaType(), mediaType);
        if (assetType != AssetType.AUDIO && assetType != AssetType.IMAGE && assetType != AssetType.VIDEO) {
            throw new ResponseStatusException(BAD_REQUEST, "Only image, video, or audio attachments are supported.");
        }

        String safeName = Optional.ofNullable(file.getOriginalFilename())
            .map(this::sanitizeFilename)
            .filter((s) -> !s.isBlank())
            .orElse("script_attachment_" + System.currentTimeMillis());

        ScriptAssetFile attachmentFile = new ScriptAssetFile(asset.getBroadcaster(), assetType);
        attachmentFile.setMediaType(optimized.mediaType());
        attachmentFile.setOriginalMediaType(mediaType);
        scriptAssetFileRepository.save(attachmentFile);

        ScriptAssetAttachment attachment = new ScriptAssetAttachment(asset.getId(), safeName);
        attachment.setFileId(attachmentFile.getId());
        attachment.setMediaType(optimized.mediaType());
        attachment.setOriginalMediaType(mediaType);
        attachment.setAssetType(assetType);

        assetStorageService.storeAsset(
            attachmentFile.getBroadcaster(),
            attachmentFile.getId(),
            optimized.bytes(),
            optimized.mediaType()
        );
        attachment = scriptAssetAttachmentRepository.save(attachment);
        ScriptAssetAttachmentView view = ScriptAssetAttachmentView.fromAttachment(asset.getBroadcaster(), attachment);

        ScriptAsset script = scriptAssetRepository
            .findById(asset.getId())
            .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Asset is not a script"));
        script.setAttachments(loadScriptAttachments(asset.getBroadcaster(), asset.getId(), null));
        AssetView scriptView = AssetView.fromScript(asset.getBroadcaster(), asset, script);
        messagingTemplate.convertAndSend(topicFor(broadcaster), AssetEvent.updated(broadcaster, scriptView));

        return Optional.of(view);
    }

    public boolean deleteScriptAttachment(String broadcaster, String scriptAssetId, String attachmentId) {
        Asset asset = requireScriptAssetForBroadcaster(broadcaster, scriptAssetId);
        ScriptAssetAttachment attachment = scriptAssetAttachmentRepository
            .findById(attachmentId)
            .filter((item) -> item.getScriptAssetId().equals(asset.getId()))
            .orElse(null);
        if (attachment == null) {
            return false;
        }
        String fileId = attachment.getFileId() != null ? attachment.getFileId() : attachment.getId();
        scriptAssetAttachmentRepository.deleteById(attachment.getId());
        removeScriptAssetFileIfOrphaned(fileId);

        ScriptAsset script = scriptAssetRepository
            .findById(asset.getId())
            .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Asset is not a script"));
        script.setAttachments(loadScriptAttachments(asset.getBroadcaster(), asset.getId(), null));
        AssetView scriptView = AssetView.fromScript(asset.getBroadcaster(), asset, script);
        messagingTemplate.convertAndSend(topicFor(broadcaster), AssetEvent.updated(broadcaster, scriptView));
        return true;
    }

    public Optional<AssetContent> getScriptAttachmentContent(
        String broadcaster,
        String scriptAssetId,
        String attachmentId
    ) {
        Asset asset = assetRepository
            .findById(scriptAssetId)
            .filter((stored) -> normalize(broadcaster).equals(stored.getBroadcaster()))
            .filter((stored) -> stored.getAssetType() == AssetType.SCRIPT)
            .orElse(null);
        if (asset == null) {
            return Optional.empty();
        }
        return scriptAssetAttachmentRepository
            .findById(attachmentId)
            .filter((item) -> item.getScriptAssetId().equals(scriptAssetId))
            .flatMap((attachment) -> loadScriptAttachmentContent(asset, attachment));
    }

    public Optional<AssetContent> getScriptLogoContent(String broadcaster, String scriptAssetId) {
        Asset asset = requireScriptAssetForBroadcaster(broadcaster, scriptAssetId);
        return scriptAssetRepository
            .findById(asset.getId())
            .map(ScriptAsset::getLogoFileId)
            .flatMap((logoFileId) -> scriptAssetFileRepository.findById(logoFileId))
            .flatMap((file) ->
                assetStorageService.loadAssetFileSafely(file.getBroadcaster(), file.getId(), file.getMediaType())
            );
    }

    public Optional<AssetContent> getAssetPreview(String assetId, boolean includeHidden) {
        return assetRepository.findById(assetId).flatMap((asset) -> loadAssetPreview(asset, includeHidden));
    }

    public boolean isAdmin(String broadcaster, String username) {
        return channelRepository
            .findById(normalize(broadcaster))
            .map(Channel::getAdmins)
            .map((admins) -> admins.contains(normalize(username)))
            .orElse(false);
    }

    public Collection<String> adminChannelsFor(String username) {
        if (username == null) return List.of();
        String login = username.toLowerCase();
        return channelRepository
            .findAll()
            .stream()
            .filter((c) -> c.getAdmins().contains(login))
            .map(Channel::getBroadcaster)
            .toList();
    }

    private String normalize(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private boolean isCodeMediaType(String mediaType) {
        if (mediaType == null || mediaType.isBlank()) {
            return false;
        }
        String normalized = mediaType.toLowerCase(Locale.ROOT);
        return normalized.startsWith("application/javascript") || normalized.startsWith("text/javascript");
    }

    private void validateCodeAssetSource(String source) {
        if (source == null || source.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Script source is required");
        }
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private void removeScriptAssetFileIfOrphaned(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            return;
        }
        long attachmentRefs = scriptAssetAttachmentRepository.countByFileId(fileId);
        long sourceRefs = scriptAssetRepository.countBySourceFileId(fileId);
        long logoRefs = scriptAssetRepository.countByLogoFileId(fileId);
        if (attachmentRefs + sourceRefs + logoRefs > 0) {
            return;
        }
        scriptAssetFileRepository
            .findById(fileId)
            .ifPresent((file) -> {
                assetStorageService.deleteAsset(file.getBroadcaster(), file.getId(), file.getMediaType(), false);
                scriptAssetFileRepository.delete(file);
            });
    }

    private void enforceUploadLimit(long sizeBytes) {
        if (sizeBytes > uploadLimitBytes) {
            throw new ResponseStatusException(
                PAYLOAD_TOO_LARGE,
                String.format(
                    "Uploaded file is too large (%d bytes). Maximum allowed is %d bytes.",
                    sizeBytes,
                    uploadLimitBytes
                )
            );
        }
    }

    private String topicFor(String broadcaster) {
        return "/topic/channel/" + broadcaster.toLowerCase(Locale.ROOT);
    }

    private List<AssetView> sortAndMapAssets(String broadcaster, Collection<Asset> assets) {
        List<String> audioIds = assets
            .stream()
            .filter((asset) -> asset.getAssetType() == AssetType.AUDIO)
            .map(Asset::getId)
            .toList();
        List<String> scriptIds = assets
            .stream()
            .filter((asset) -> asset.getAssetType() == AssetType.SCRIPT)
            .map(Asset::getId)
            .toList();
        List<String> visualIds = assets
            .stream()
            .filter(
                (asset) ->
                    asset.getAssetType() == AssetType.IMAGE ||
                    asset.getAssetType() == AssetType.VIDEO ||
                    asset.getAssetType() == AssetType.OTHER
            )
            .map(Asset::getId)
            .toList();

        Map<String, VisualAsset> visuals = visualAssetRepository
            .findByIdIn(visualIds)
            .stream()
            .collect(Collectors.toMap(VisualAsset::getId, (asset) -> asset));
        Map<String, AudioAsset> audios = audioAssetRepository
            .findByIdIn(audioIds)
            .stream()
            .collect(Collectors.toMap(AudioAsset::getId, (asset) -> asset));
        Map<String, ScriptAsset> scripts = scriptAssetRepository
            .findByIdIn(scriptIds)
            .stream()
            .collect(Collectors.toMap(ScriptAsset::getId, (asset) -> asset));
        Map<String, List<ScriptAssetAttachmentView>> scriptAttachments = scriptIds.isEmpty()
            ? Map.of()
            : Optional.ofNullable(scriptAssetAttachmentRepository.findByScriptAssetIdIn(scriptIds))
                .orElse(List.of())
                .stream()
                .collect(
                    Collectors.groupingBy(
                        ScriptAssetAttachment::getScriptAssetId,
                        Collectors.mapping(
                            (attachment) -> ScriptAssetAttachmentView.fromAttachment(broadcaster, attachment),
                            Collectors.toList()
                        )
                    )
                );

        return assets
            .stream()
            .map((asset) -> resolveAssetView(broadcaster, asset, visuals, audios, scripts, scriptAttachments))
            .filter(Objects::nonNull)
            .sorted(
                Comparator.comparing((AssetView view) ->
                    view.zIndex() == null ? Integer.MAX_VALUE : view.zIndex()
                ).thenComparing(AssetView::createdAt, Comparator.nullsFirst(Comparator.naturalOrder()))
            )
            .toList();
    }

    private int nextZIndex(String broadcaster) {
        return (
            visualAssetRepository
                .findByIdIn(assetsWithType(normalize(broadcaster), AssetType.IMAGE, AssetType.VIDEO, AssetType.OTHER))
                .stream()
                .mapToInt(VisualAsset::getZIndex)
                .max()
                .orElse(0) +
            1
        );
    }

    private List<String> assetsWithType(String broadcaster, AssetType... types) {
        Set<AssetType> typeSet = EnumSet.noneOf(AssetType.class);
        typeSet.addAll(Arrays.asList(types));
        return assetRepository
            .findByBroadcaster(normalize(broadcaster))
            .stream()
            .filter((asset) -> typeSet.contains(asset.getAssetType()))
            .map(Asset::getId)
            .toList();
    }

    private AssetView resolveAssetView(String broadcaster, Asset asset) {
        return resolveAssetView(broadcaster, asset, null, null, null, null);
    }

    private AssetView resolveAssetView(
        String broadcaster,
        Asset asset,
        Map<String, VisualAsset> visuals,
        Map<String, AudioAsset> audios,
        Map<String, ScriptAsset> scripts,
        Map<String, List<ScriptAssetAttachmentView>> scriptAttachments
    ) {
        if (asset.getAssetType() == AssetType.AUDIO) {
            AudioAsset audio = audios != null
                ? audios.get(asset.getId())
                : audioAssetRepository.findById(asset.getId()).orElse(null);
            return audio == null ? null : AssetView.fromAudio(broadcaster, asset, audio);
        }
        if (asset.getAssetType() == AssetType.SCRIPT) {
            ScriptAsset script = scripts != null
                ? scripts.get(asset.getId())
                : scriptAssetRepository.findById(asset.getId()).orElse(null);
            if (script != null) {
                script.setAttachments(loadScriptAttachments(broadcaster, asset.getId(), scriptAttachments));
            }
            return script == null ? null : AssetView.fromScript(broadcaster, asset, script);
        }
        VisualAsset visual = visuals != null
            ? visuals.get(asset.getId())
            : visualAssetRepository.findById(asset.getId()).orElse(null);
        return visual == null ? null : AssetView.fromVisual(broadcaster, asset, visual);
    }

    private Optional<AssetContent> loadAssetContent(Asset asset) {
        switch (asset.getAssetType()) {
            case AUDIO -> {
                return audioAssetRepository
                    .findById(asset.getId())
                    .flatMap((audio) ->
                        assetStorageService.loadAssetFileSafely(
                            asset.getBroadcaster(),
                            asset.getId(),
                            audio.getMediaType()
                        )
                    );
            }
            case SCRIPT -> {
                return scriptAssetRepository
                    .findById(asset.getId())
                    .flatMap((script) -> loadScriptSourceContent(asset, script));
            }
            default -> {
                return visualAssetRepository
                    .findById(asset.getId())
                    .flatMap((visual) ->
                        assetStorageService.loadAssetFileSafely(
                            asset.getBroadcaster(),
                            asset.getId(),
                            visual.getMediaType()
                        )
                    );
            }
        }
    }

    private Optional<AssetContent> loadScriptSourceContent(Asset asset, ScriptAsset script) {
        if (script == null || asset == null) {
            return Optional.empty();
        }
        String sourceFileId = script.getSourceFileId() != null ? script.getSourceFileId() : script.getId();
        return scriptAssetFileRepository
            .findById(sourceFileId)
            .flatMap((file) ->
                assetStorageService.loadAssetFileSafely(file.getBroadcaster(), file.getId(), file.getMediaType())
            )
            .or(() ->
                assetStorageService.loadAssetFileSafely(
                    asset.getBroadcaster(),
                    sourceFileId,
                    script.getMediaType()
                )
            );
    }

    private Optional<AssetContent> loadScriptAttachmentContent(Asset asset, ScriptAssetAttachment attachment) {
        if (attachment == null || asset == null) {
            return Optional.empty();
        }
        String fileId = attachment.getFileId() != null ? attachment.getFileId() : attachment.getId();
        return scriptAssetFileRepository
            .findById(fileId)
            .flatMap((file) ->
                assetStorageService.loadAssetFileSafely(file.getBroadcaster(), file.getId(), file.getMediaType())
            )
            .or(() ->
                assetStorageService.loadAssetFileSafely(
                    asset.getBroadcaster(),
                    fileId,
                    attachment.getMediaType()
                )
            );
    }

    private List<ScriptAssetAttachmentView> loadScriptAttachments(
        String broadcaster,
        String scriptAssetId,
        Map<String, List<ScriptAssetAttachmentView>> scriptAttachments
    ) {
        if (scriptAttachments != null) {
            return scriptAttachments.getOrDefault(scriptAssetId, List.of());
        }
        List<ScriptAssetAttachment> attachments = Optional.ofNullable(
            scriptAssetAttachmentRepository.findByScriptAssetId(scriptAssetId)
        )
            .orElse(List.of());
        return attachments
            .stream()
            .map((attachment) -> ScriptAssetAttachmentView.fromAttachment(broadcaster, attachment))
            .toList();
    }

    private Asset requireScriptAssetForBroadcaster(String broadcaster, String scriptAssetId) {
        String normalized = normalize(broadcaster);
        return assetRepository
            .findById(scriptAssetId)
            .filter((asset) -> normalized.equals(asset.getBroadcaster()))
            .filter((asset) -> asset.getAssetType() == AssetType.SCRIPT)
            .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Asset is not a script"));
    }

    private Optional<AssetContent> loadAssetPreview(Asset asset, boolean includeHidden) {
        if (
            asset.getAssetType() != AssetType.VIDEO &&
            asset.getAssetType() != AssetType.IMAGE &&
            asset.getAssetType() != AssetType.OTHER
        ) {
            return Optional.empty();
        }
        return visualAssetRepository
            .findById(asset.getId())
            .filter((visual) -> includeHidden || !visual.isHidden())
            .flatMap((visual) ->
                assetStorageService.loadPreviewSafely(
                    asset.getBroadcaster(),
                    asset.getId(),
                    visual.getPreview() != null && !visual.getPreview().isBlank()
                )
            );
    }

    private void deleteAssetStorage(Asset asset) {
        switch (asset.getAssetType()) {
            case AUDIO -> audioAssetRepository
                .findById(asset.getId())
                .ifPresent((audio) ->
                    assetStorageService.deleteAsset(asset.getBroadcaster(), asset.getId(), audio.getMediaType(), false)
                );
            case SCRIPT -> scriptAssetRepository
                .findById(asset.getId())
                .ifPresent((script) -> {
                    removeScriptAssetFileIfOrphaned(script.getSourceFileId());
                    removeScriptAssetFileIfOrphaned(script.getLogoFileId());
                    scriptAssetAttachmentRepository
                        .findByScriptAssetId(asset.getId())
                        .forEach((attachment) ->
                            removeScriptAssetFileIfOrphaned(
                                attachment.getFileId() != null ? attachment.getFileId() : attachment.getId()
                            )
                        );
                });
            default -> visualAssetRepository
                .findById(asset.getId())
                .ifPresent((visual) ->
                    assetStorageService.deleteAsset(
                        asset.getBroadcaster(),
                        asset.getId(),
                        visual.getMediaType(),
                        visual.getPreview() != null && !visual.getPreview().isBlank()
                    )
                );
        }
    }

    private boolean hasPatchChanges(AssetPatch patch) {
        return (
            patch.x() != null ||
            patch.y() != null ||
            patch.width() != null ||
            patch.height() != null ||
            patch.rotation() != null ||
            patch.speed() != null ||
            patch.muted() != null ||
            patch.zIndex() != null ||
            patch.hidden() != null ||
            patch.audioLoop() != null ||
            patch.audioDelayMillis() != null ||
            patch.audioSpeed() != null ||
            patch.audioPitch() != null ||
            patch.audioVolume() != null
        );
    }
}
