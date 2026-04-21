package dev.kruhlmann.imgfloat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class VersionServiceTest {

    @Test
    void returnsVersionWhenPomXmlPresent() {
        // VersionService reads pom.xml from the filesystem during development;
        // in the test classpath pom.xml is available at the project root.
        VersionService service = new VersionService();
        String version = service.getVersion();
        assertThat(version).isNotBlank();
        // Should look like a semver string, not "unknown" or empty
        assertThat(version).matches("[0-9]+\\.[0-9]+.*");
    }

    @Test
    void versionIsConsistentAcrossCalls() {
        VersionService service = new VersionService();
        assertThat(service.getVersion()).isEqualTo(service.getVersion());
    }
}
