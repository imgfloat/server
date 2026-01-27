package dev.kruhlmann.imgfloat.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class GitInfoService {

    private static final String FALLBACK_GIT_SHA = "unknown";
    private static final Logger LOG = LoggerFactory.getLogger(GitInfoService.class);

    private final String commitSha;
    private final String shortCommitSha;
    private final String commitUrlPrefix;

    public GitInfoService(@Value("${IMGFLOAT_COMMIT_URL_PREFIX:}") String commitUrlPrefix) {
        CommitInfo commitInfo = resolveFromGitProperties();
        if (commitInfo == null) {
            commitInfo = resolveFromGitBinary();
        }

        String full = commitInfo != null ? commitInfo.fullSha() : null;
        String abbreviated = commitInfo != null ? commitInfo.shortSha() : null;

        if (abbreviated == null && full != null) {
            abbreviated = abbreviate(full);
        } else if (full == null && abbreviated != null) {
            full = abbreviated;
        }

        this.commitSha = defaultValue(full);
        this.shortCommitSha = defaultValue(abbreviated);
        this.commitUrlPrefix = normalize(commitUrlPrefix);
    }

    public String getShortCommitSha() {
        return shortCommitSha;
    }

    public String getCommitUrl() {
        if (!shouldShowCommitChip()
            || commitSha == null
            || commitSha.isBlank()
            || FALLBACK_GIT_SHA.equalsIgnoreCase(commitSha)) {
            return null;
        }
        return commitUrlPrefix + commitSha;
    }

    public boolean shouldShowCommitChip() {
        return StringUtils.hasText(commitUrlPrefix);
    }

    private CommitInfo resolveFromGitProperties() {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("git.properties")) {
            if (inputStream == null) {
                return null;
            }
            Properties properties = new Properties();
            properties.load(inputStream);
            String fullSha = normalize(properties.getProperty("git.commit.id"));
            String shortSha = normalize(properties.getProperty("git.commit.id.abbrev"));
            if (fullSha == null && shortSha == null) {
                return null;
            }
            return new CommitInfo(fullSha, shortSha);
        } catch (IOException e) {
            LOG.warn("Unable to read git.properties from classpath", e);
            return null;
        }
    }

    private CommitInfo resolveFromGitBinary() {
        String fullSha = runGitCommand("rev-parse", "HEAD");
        String shortSha = runGitCommand("rev-parse", "--short", "HEAD");
        if (fullSha == null && shortSha == null) {
            return null;
        }
        return new CommitInfo(fullSha, shortSha);
    }

    private String runGitCommand(String... command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String output = reader.readLine();
                int exitCode = process.waitFor();
                if (exitCode == 0 && output != null && !output.isBlank()) {
                    return output.trim();
                }
                LOG.debug("Git command {} failed with exit code {}", String.join(" ", command), exitCode);
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.debug("Thread interrupt during git command {}", String.join(" ", command), e);
            return null;
        } catch (IOException e) {
            LOG.debug("Git command IO error command {}", String.join(" ", command), e);
            return null;
        }
    }

    private String abbreviate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 7 ? value.substring(0, 7) : value;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String defaultValue(String value) {
        if (value == null || value.isBlank()) {
            return FALLBACK_GIT_SHA;
        }
        return value;
    }

    private record CommitInfo(String fullSha, String shortSha) {}
}
