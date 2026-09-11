package com.giftmarket.global.diagnostic;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.boot.context.metrics.buffering.StartupTimeline;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.stream.StreamSupport;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "startup.diagnostics.enabled",
        havingValue = "true"
)
public class StartupTimelineLogger {

    private static final long MINIMUM_LOG_DURATION_MILLIS = 1_000L;

    private final BufferingApplicationStartup applicationStartup;

    @EventListener(ApplicationReadyEvent.class)
    public void logSlowStartupSteps() {
        StartupTimeline timeline = applicationStartup.getBufferedTimeline();

        log.info(
                "Startup diagnostics enabled. bufferedStepCount={}, slowStepThresholdMillis={}",
                timeline.getEvents().size(),
                MINIMUM_LOG_DURATION_MILLIS
        );

        timeline.getEvents().stream()
                .filter(event -> event.getDuration().toMillis() >= MINIMUM_LOG_DURATION_MILLIS)
                .sorted(Comparator.comparing(
                        StartupTimeline.TimelineEvent::getDuration
                ).reversed())
                .forEach(event -> log.info(
                        "Startup step: durationMillis={}, name={}, tags={}",
                        event.getDuration().toMillis(),
                        event.getStartupStep().getName(),
                        StreamSupport.stream(
                                        event.getStartupStep().getTags().spliterator(),
                                        false
                                )
                                .map(tag -> tag.getKey() + "=" + tag.getValue())
                                .toList()
                ));
    }
}
