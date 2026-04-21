package dev.kruhlmann.imgfloat.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import dev.kruhlmann.imgfloat.model.api.request.CanvasSettingsRequest;
import dev.kruhlmann.imgfloat.model.api.request.ChannelScriptSettingsRequest;
import dev.kruhlmann.imgfloat.model.api.response.CanvasEvent;
import dev.kruhlmann.imgfloat.model.db.imgfloat.Channel;
import dev.kruhlmann.imgfloat.model.db.imgfloat.Settings;
import dev.kruhlmann.imgfloat.repository.ChannelRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import dev.kruhlmann.imgfloat.util.StringNormalizer;

/**
 * Manages per-channel canvas and script-overlay settings.
 * Extracted from {@link ChannelDirectoryService} to give it a focused responsibility.
 */
@Service
public class ChannelSettingsService {

    private final ChannelRepository channelRepository;
    private final SettingsService settingsService;
    private final SimpMessagingTemplate messagingTemplate;
    private final AuditLogService auditLogService;

    public ChannelSettingsService(
        ChannelRepository channelRepository,
        SettingsService settingsService,
        SimpMessagingTemplate messagingTemplate,
        AuditLogService auditLogService
    ) {
        this.channelRepository = channelRepository;
        this.settingsService = settingsService;
        this.messagingTemplate = messagingTemplate;
        this.auditLogService = auditLogService;
    }

    public CanvasSettingsRequest getCanvasSettings(String broadcaster) {
        Channel channel = getOrCreateChannel(broadcaster);
        return new CanvasSettingsRequest(
            channel.getCanvasWidth(),
            channel.getCanvasHeight(),
            channel.getMaxVolumeDb()
        );
    }

    public CanvasSettingsRequest updateCanvasSettings(String broadcaster, CanvasSettingsRequest req, String actor) {
        validateCanvasSettings(req);
        Channel channel = getOrCreateChannel(broadcaster);
        double beforeWidth = channel.getCanvasWidth();
        double beforeHeight = channel.getCanvasHeight();
        Double beforeMaxVolumeDb = channel.getMaxVolumeDb();

        channel.setCanvasWidth(req.getWidth());
        channel.setCanvasHeight(req.getHeight());
        if (req.getMaxVolumeDb() != null) {
            channel.setMaxVolumeDb(req.getMaxVolumeDb());
        }
        channelRepository.save(channel);

        CanvasSettingsRequest response = new CanvasSettingsRequest(
            channel.getCanvasWidth(),
            channel.getCanvasHeight(),
            channel.getMaxVolumeDb()
        );
        messagingTemplate.convertAndSend(topicFor(broadcaster), CanvasEvent.updated(broadcaster, response));

        boolean changed =
            beforeWidth != channel.getCanvasWidth() ||
            beforeHeight != channel.getCanvasHeight() ||
            !Objects.equals(beforeMaxVolumeDb, channel.getMaxVolumeDb());

        if (changed) {
            List<String> changes = new ArrayList<>();
            if (beforeWidth != channel.getCanvasWidth() || beforeHeight != channel.getCanvasHeight()) {
                changes.add(
                    String.format(
                        Locale.ROOT,
                        "canvas %.0fx%.0f -> %.0fx%.0f",
                        beforeWidth,
                        beforeHeight,
                        channel.getCanvasWidth(),
                        channel.getCanvasHeight()
                    )
                );
            }
            if (!Objects.equals(beforeMaxVolumeDb, channel.getMaxVolumeDb())) {
                changes.add(
                    String.format(
                        Locale.ROOT,
                        "max volume %.0f dB -> %.0f dB",
                        beforeMaxVolumeDb == null ? 0.0 : beforeMaxVolumeDb,
                        channel.getMaxVolumeDb() == null ? 0.0 : channel.getMaxVolumeDb()
                    )
                );
            }
            auditLogService.recordEntry(
                channel.getBroadcaster(),
                actor,
                "CANVAS_UPDATED",
                "Canvas settings updated" + (changes.isEmpty() ? "" : " (" + String.join(", ", changes) + ")")
            );
        }
        return response;
    }

    public ChannelScriptSettingsRequest getChannelScriptSettings(String broadcaster) {
        Channel channel = getOrCreateChannel(broadcaster);
        return new ChannelScriptSettingsRequest(
            channel.isAllowChannelEmotesForAssets(),
            channel.isAllowSevenTvEmotesForAssets(),
            channel.isAllowScriptChatAccess()
        );
    }

    public ChannelScriptSettingsRequest updateChannelScriptSettings(
        String broadcaster,
        ChannelScriptSettingsRequest request,
        String actor
    ) {
        Channel channel = getOrCreateChannel(broadcaster);
        boolean beforeChannelEmotes = channel.isAllowChannelEmotesForAssets();
        boolean beforeSevenTv = channel.isAllowSevenTvEmotesForAssets();
        boolean beforeChatAccess = channel.isAllowScriptChatAccess();

        channel.setAllowChannelEmotesForAssets(request.isAllowChannelEmotesForAssets());
        channel.setAllowSevenTvEmotesForAssets(request.isAllowSevenTvEmotesForAssets());
        channel.setAllowScriptChatAccess(request.isAllowScriptChatAccess());
        channelRepository.save(channel);

        boolean changed =
            beforeChannelEmotes != channel.isAllowChannelEmotesForAssets() ||
            beforeSevenTv != channel.isAllowSevenTvEmotesForAssets() ||
            beforeChatAccess != channel.isAllowScriptChatAccess();

        if (changed) {
            List<String> changes = new ArrayList<>();
            if (beforeChannelEmotes != channel.isAllowChannelEmotesForAssets()) {
                changes.add("channelEmotes: " + beforeChannelEmotes + " -> " + channel.isAllowChannelEmotesForAssets());
            }
            if (beforeSevenTv != channel.isAllowSevenTvEmotesForAssets()) {
                changes.add("sevenTvEmotes: " + beforeSevenTv + " -> " + channel.isAllowSevenTvEmotesForAssets());
            }
            if (beforeChatAccess != channel.isAllowScriptChatAccess()) {
                changes.add("scriptChatAccess: " + beforeChatAccess + " -> " + channel.isAllowScriptChatAccess());
            }
            auditLogService.recordEntry(
                channel.getBroadcaster(),
                actor,
                "SCRIPT_SETTINGS_UPDATED",
                "Script settings updated" + (changes.isEmpty() ? "" : " (" + String.join(", ", changes) + ")")
            );
        }
        return new ChannelScriptSettingsRequest(
            channel.isAllowChannelEmotesForAssets(),
            channel.isAllowSevenTvEmotesForAssets(),
            channel.isAllowScriptChatAccess()
        );
    }

    private void validateCanvasSettings(CanvasSettingsRequest req) {
        Settings settings = settingsService.get();
        int max = settings.getMaxCanvasSideLengthPixels();

        if (req.getWidth() <= 0 || req.getWidth() > max || !Double.isFinite(req.getWidth()) || req.getWidth() % 1 != 0) {
            throw new ResponseStatusException(
                BAD_REQUEST,
                "Canvas width must be a whole number within [1 to " + max + "]"
            );
        }
        if (req.getHeight() <= 0 || req.getHeight() > max || !Double.isFinite(req.getHeight()) || req.getHeight() % 1 != 0) {
            throw new ResponseStatusException(
                BAD_REQUEST,
                "Canvas height must be a whole number within [1 to " + max + "]"
            );
        }
        if (req.getMaxVolumeDb() != null) {
            double db = req.getMaxVolumeDb();
            if (!Double.isFinite(db) || db < -60 || db > 0) {
                throw new ResponseStatusException(BAD_REQUEST, "Max volume must be within [-60 to 0] dB");
            }
        }
    }

    private Channel getOrCreateChannel(String broadcaster) {
        String normalized = normalize(broadcaster);
        return channelRepository.findById(normalized)
            .orElseGet(() -> channelRepository.save(new Channel(normalized)));
    }

    private String topicFor(String broadcaster) {
        return "/topic/channel/" + normalize(broadcaster);
    }

    private String normalize(String value) {
        return StringNormalizer.toLowerCaseRoot(value);
    }
}
