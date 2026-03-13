package dev.kruhlmann.imgfloat.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kruhlmann.imgfloat.service.git.GitCommitInfo;
import dev.kruhlmann.imgfloat.service.git.GitCommitInfoSource;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GitInfoServiceTest {

    @Test
    void usesFirstCommitInfoSource() {
        TrackingSource first = new TrackingSource(Optional.of(new GitCommitInfo("full-sha", "short")));
        TrackingSource second = new TrackingSource(Optional.of(new GitCommitInfo("other-sha", "other")));

        GitInfoService service = new GitInfoService("https://example/commit/", List.of(first, second));

        assertThat(service.getShortCommitSha()).isEqualTo("short");
        assertThat(service.getCommitUrl()).isEqualTo("https://example/commit/full-sha");
        assertThat(first.calls()).isEqualTo(1);
        assertThat(second.calls()).isEqualTo(0);
    }

    @Test
    void fallsBackToNextSourceWhenFirstIsEmpty() {
        TrackingSource first = new TrackingSource(Optional.empty());
        TrackingSource second = new TrackingSource(Optional.of(new GitCommitInfo(null, "abc1234")));

        GitInfoService service = new GitInfoService("https://example/commit/", List.of(first, second));

        assertThat(service.getShortCommitSha()).isEqualTo("abc1234");
        assertThat(service.getCommitUrl()).isEqualTo("https://example/commit/abc1234");
        assertThat(first.calls()).isEqualTo(1);
        assertThat(second.calls()).isEqualTo(1);
    }

    @Test
    void abbreviatesShortShaWhenOnlyFullIsProvided() {
        GitInfoService service = new GitInfoService(
            "https://example/commit/",
            List.of(() -> Optional.of(new GitCommitInfo("1234567890", null)))
        );

        assertThat(service.getShortCommitSha()).isEqualTo("1234567");
        assertThat(service.getCommitUrl()).isEqualTo("https://example/commit/1234567890");
    }

    @Test
    void hidesCommitUrlWhenPrefixOrShaIsMissing() {
        GitInfoService missingPrefix = new GitInfoService(" ", List.of());
        GitInfoService missingSha = new GitInfoService("https://example/commit/", List.of());

        assertThat(missingPrefix.shouldShowCommitChip()).isFalse();
        assertThat(missingPrefix.getCommitUrl()).isNull();
        assertThat(missingSha.getShortCommitSha()).isEqualTo("unknown");
        assertThat(missingSha.getCommitUrl()).isNull();
    }

    private static final class TrackingSource implements GitCommitInfoSource {
        private final Optional<GitCommitInfo> payload;
        private final AtomicInteger calls = new AtomicInteger();

        private TrackingSource(Optional<GitCommitInfo> payload) {
            this.payload = payload;
        }

        @Override
        public Optional<GitCommitInfo> loadCommitInfo() {
            calls.incrementAndGet();
            return payload;
        }

        private int calls() {
            return calls.get();
        }
    }
}
