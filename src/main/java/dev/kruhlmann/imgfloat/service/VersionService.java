package dev.kruhlmann.imgfloat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

@Component
public class VersionService {
    private static final Logger LOG = LoggerFactory.getLogger(VersionService.class);
    private final String version;

    public VersionService() {
        this.version = resolveVersion();
    }

    public String getVersion() {
        return version;
    }

    private String resolveVersion() {
        String manifestVersion = getClass().getPackage().getImplementationVersion();
        if (manifestVersion != null && !manifestVersion.isBlank()) {
            return manifestVersion;
        }

        String gitDescribeVersion = getGitVersionString();
        if (gitDescribeVersion != null) {
            return "git-" + gitDescribeVersion;
        }

        return "unknown";
    }

    private String getGitVersionString() {
        Process process = null;
        try {
            process = new ProcessBuilder("git", "describe", "--tags", "--always")
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String result = reader.readLine();
                int exitCode = process.waitFor();
                if (exitCode == 0 && result != null && !result.isBlank()) {
                    return result.trim();
                }
                LOG.warn("git describe returned exit code {} with output: {}", exitCode, result);
            }
        } catch (IOException e) {
            LOG.warn("Unable to determine git version using git describe", e);
            if (process != null) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            LOG.warn("Interrupted while determining git version", e);
            if (process != null) {
                process.destroyForcibly();
            }
            Thread.currentThread().interrupt();
        }
        return null;
    }
}
