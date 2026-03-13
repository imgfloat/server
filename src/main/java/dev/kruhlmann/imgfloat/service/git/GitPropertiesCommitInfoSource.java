package dev.kruhlmann.imgfloat.service.git;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Order(0)
public class GitPropertiesCommitInfoSource implements GitCommitInfoSource {

    private static final Logger LOG = LoggerFactory.getLogger(GitPropertiesCommitInfoSource.class);

    @Override
    public Optional<GitCommitInfo> loadCommitInfo() {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("git.properties")) {
            if (inputStream == null) {
                return Optional.empty();
            }
            Properties properties = new Properties();
            properties.load(inputStream);
            String fullSha = properties.getProperty("git.commit.id");
            String shortSha = properties.getProperty("git.commit.id.abbrev");
            if (!StringUtils.hasText(fullSha) && !StringUtils.hasText(shortSha)) {
                return Optional.empty();
            }
            return Optional.of(new GitCommitInfo(fullSha, shortSha));
        } catch (IOException e) {
            LOG.warn("Unable to read git.properties from classpath", e);
            return Optional.empty();
        }
    }
}
