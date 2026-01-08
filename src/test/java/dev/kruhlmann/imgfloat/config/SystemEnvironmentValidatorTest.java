package dev.kruhlmann.imgfloat.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

class SystemEnvironmentValidatorTest {

    @Test
    void validateSkipsWhenBootstrappedForTests() {
        Environment environment = new MockEnvironment().withProperty(
            "org.springframework.boot.test.context.SpringBootTestContextBootstrapper",
            "true"
        );
        SystemEnvironmentValidator validator = new SystemEnvironmentValidator(environment);

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void validateThrowsWhenRequiredFieldsMissing() {
        Environment environment = new MockEnvironment();
        SystemEnvironmentValidator validator = new SystemEnvironmentValidator(environment);
        setRequiredFields(validator);
        ReflectionTestUtils.setField(validator, "twitchClientId", "");

        assertThatThrownBy(validator::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("TWITCH_CLIENT_ID");
    }

    @Test
    void validateAcceptsAllRequiredFields() {
        Environment environment = new MockEnvironment();
        SystemEnvironmentValidator validator = new SystemEnvironmentValidator(environment);
        setRequiredFields(validator);

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    private void setRequiredFields(SystemEnvironmentValidator validator) {
        ReflectionTestUtils.setField(validator, "twitchClientId", "client-id");
        ReflectionTestUtils.setField(validator, "twitchClientSecret", "client-secret");
        ReflectionTestUtils.setField(validator, "springMaxFileSize", "10MB");
        ReflectionTestUtils.setField(validator, "springMaxRequestSize", "12MB");
        ReflectionTestUtils.setField(validator, "assetsPath", "/tmp/assets");
        ReflectionTestUtils.setField(validator, "previewsPath", "/tmp/previews");
        ReflectionTestUtils.setField(validator, "dbPath", "/tmp/db");
        ReflectionTestUtils.setField(validator, "initialSysadmin", "admin");
        ReflectionTestUtils.setField(validator, "githubOwner", "owner");
        ReflectionTestUtils.setField(validator, "githubRepo", "repo");
    }
}
