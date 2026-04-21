package dev.kruhlmann.imgfloat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GithubReleaseServiceTest {

    @Test
    void returnsDownloadBaseUrlWhenConfigured() {
        GithubReleaseService service = new GithubReleaseService("imgfloat", "client", "1.2.3");
        String url = service.getDownloadBaseUrl();
        assertThat(url).startsWith("https://github.com/imgfloat/client/releases/download/v1.2.3/");
    }

    @Test
    void stripsLeadingVFromVersion() {
        GithubReleaseService service = new GithubReleaseService("imgfloat", "client", "v1.2.3");
        assertThat(service.getDownloadBaseUrl()).contains("/v1.2.3/");
        assertThat(service.getClientReleaseVersion()).isEqualTo("1.2.3");
    }

    @Test
    void stripsSnapshotSuffix() {
        GithubReleaseService service = new GithubReleaseService("imgfloat", "client", "1.2.3-SNAPSHOT");
        assertThat(service.getClientReleaseVersion()).isEqualTo("1.2.3");
        assertThat(service.getDownloadBaseUrl()).contains("/v1.2.3/");
    }

    @Test
    void throwsWhenOwnerMissing() {
        GithubReleaseService service = new GithubReleaseService(null, "client", "1.0.0");
        assertThatThrownBy(service::getDownloadBaseUrl)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GitHub client configuration");
    }

    @Test
    void throwsWhenRepoMissing() {
        GithubReleaseService service = new GithubReleaseService("imgfloat", null, "1.0.0");
        assertThatThrownBy(service::getDownloadBaseUrl).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void throwsWhenVersionMissing() {
        GithubReleaseService service = new GithubReleaseService("imgfloat", "client", null);
        assertThatThrownBy(service::getDownloadBaseUrl).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void throwsWhenVersionBlank() {
        GithubReleaseService service = new GithubReleaseService("imgfloat", "client", "   ");
        assertThatThrownBy(service::getDownloadBaseUrl).isInstanceOf(IllegalStateException.class);
    }
}
