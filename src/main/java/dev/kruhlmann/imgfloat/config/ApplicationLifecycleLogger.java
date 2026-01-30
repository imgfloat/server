package dev.kruhlmann.imgfloat.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ApplicationLifecycleLogger {

    private static final Logger LOG = LoggerFactory.getLogger(ApplicationLifecycleLogger.class);

    private final String serverPort;

    public ApplicationLifecycleLogger(@Value("${server.port:8080}") String serverPort) {
        this.serverPort = serverPort;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logReady() {
        LOG.info("Imgfloat ready to accept connections on port {}", serverPort);
    }
}
