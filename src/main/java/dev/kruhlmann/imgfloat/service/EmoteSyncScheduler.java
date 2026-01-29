package dev.kruhlmann.imgfloat.service;

import dev.kruhlmann.imgfloat.model.db.imgfloat.Channel;
import dev.kruhlmann.imgfloat.model.db.imgfloat.Settings;
import dev.kruhlmann.imgfloat.repository.ChannelRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Service;

@Service
public class EmoteSyncScheduler implements SchedulingConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(EmoteSyncScheduler.class);
    private static final int DEFAULT_INTERVAL_MINUTES = 60;

    private final SettingsService settingsService;
    private final ChannelRepository channelRepository;
    private final TwitchEmoteService twitchEmoteService;
    private final SevenTvEmoteService sevenTvEmoteService;
    private final TaskScheduler taskScheduler;

    public EmoteSyncScheduler(
        SettingsService settingsService,
        ChannelRepository channelRepository,
        TwitchEmoteService twitchEmoteService,
        SevenTvEmoteService sevenTvEmoteService,
        @Qualifier("emoteSyncTaskScheduler") TaskScheduler taskScheduler
    ) {
        this.settingsService = settingsService;
        this.channelRepository = channelRepository;
        this.twitchEmoteService = twitchEmoteService;
        this.sevenTvEmoteService = sevenTvEmoteService;
        this.taskScheduler = taskScheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setScheduler(taskScheduler);
        taskRegistrar.addTriggerTask(this::syncEmotes, buildTrigger());
    }

    private Trigger buildTrigger() {
        return (TriggerContext triggerContext) -> {
            int interval = resolveIntervalMinutes();
            Instant lastCompletion = resolveLastCompletion(triggerContext, interval);
            return lastCompletion.plus(Duration.ofMinutes(interval));
        };
    }

    private Instant resolveLastCompletion(TriggerContext triggerContext, int intervalMinutes) {
        Instant lastCompletion = triggerContext.lastCompletion();
        if (lastCompletion != null) {
            return lastCompletion;
        }
        Settings settings = settingsService.get();
        Instant persisted = settings.getLastEmoteSyncAt();
        return persisted != null ? persisted : Instant.now().minus(Duration.ofMinutes(intervalMinutes));
    }

    private int resolveIntervalMinutes() {
        Settings settings = settingsService.get();
        int interval = settings.getEmoteSyncIntervalMinutes();
        return interval > 0 ? interval : DEFAULT_INTERVAL_MINUTES;
    }

    private void syncEmotes() {
        int interval = resolveIntervalMinutes();
        LOG.info("Synchronizing emotes (interval {} minutes)", interval);
        List<Channel> channels = List.of();
        try {
            channels = channelRepository.findAll();
            twitchEmoteService.refreshGlobalEmotes();
            for (Channel channel : channels) {
                String broadcaster = channel.getBroadcaster();
                twitchEmoteService.refreshChannelEmotes(broadcaster);
                sevenTvEmoteService.refreshChannelEmotes(broadcaster);
            }
            LOG.info("Completed emote sync for {} channels", channels.size());
        } catch (Exception ex) {
            LOG.error("Emote sync failed", ex);
        } finally {
            settingsService.updateLastEmoteSyncAt(Instant.now());
        }
    }
}
