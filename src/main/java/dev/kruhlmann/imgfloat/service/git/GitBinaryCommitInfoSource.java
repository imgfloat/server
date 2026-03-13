package dev.kruhlmann.imgfloat.service.git;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class GitBinaryCommitInfoSource implements GitCommitInfoSource {

    private static final Logger LOG = LoggerFactory.getLogger(GitBinaryCommitInfoSource.class);

    @Override
    public Optional<GitCommitInfo> loadCommitInfo() {
        String fullSha = runGitCommand("rev-parse", "HEAD");
        String shortSha = runGitCommand("rev-parse", "--short", "HEAD");
        if (fullSha == null && shortSha == null) {
            return Optional.empty();
        }
        return Optional.of(new GitCommitInfo(fullSha, shortSha));
    }

    private String runGitCommand(String... args) {
        List<String> command = new ArrayList<>(args.length + 1);
        command.add("git");
        command.addAll(List.of(args));
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
}
