package dev.kruhlmann.imgfloat.service.git;

import java.util.Optional;

@FunctionalInterface
public interface GitCommitInfoSource {
    Optional<GitCommitInfo> loadCommitInfo();
}
