package dev.kruhlmann.imgfloat.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogSanitizerTest {

    @Test
    void sanitizeReturnsNullForNullInput() {
        assertThat(LogSanitizer.sanitize(null)).isNull();
    }

    @Test
    void sanitizeReplacesNewlinesWithUnderscores() {
        assertThat(LogSanitizer.sanitize("alpha\nBravo\r\ncharlie")).isEqualTo("alpha_Bravo_charlie");
    }

    @Test
    void sanitizeLeavesStringsWithoutNewlinesUntouched() {
        assertThat(LogSanitizer.sanitize("no-newlines-here")).isEqualTo("no-newlines-here");
    }
}
