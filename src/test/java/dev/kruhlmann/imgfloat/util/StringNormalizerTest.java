package dev.kruhlmann.imgfloat.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StringNormalizerTest {

    @Test
    void toLowerCaseRootReturnsNullForNull() {
        assertThat(StringNormalizer.toLowerCaseRoot(null)).isNull();
    }

    @Test
    void toLowerCaseRootConvertsToLowercase() {
        assertThat(StringNormalizer.toLowerCaseRoot("Hello")).isEqualTo("hello");
        assertThat(StringNormalizer.toLowerCaseRoot("BROADCASTER")).isEqualTo("broadcaster");
        assertThat(StringNormalizer.toLowerCaseRoot("MiXeDcAsE")).isEqualTo("mixedcase");
    }

    @Test
    void toLowerCaseRootHandlesAlreadyLowercase() {
        assertThat(StringNormalizer.toLowerCaseRoot("already_lower")).isEqualTo("already_lower");
    }

    @Test
    void toLowerCaseRootHandlesEmptyString() {
        assertThat(StringNormalizer.toLowerCaseRoot("")).isEqualTo("");
    }

    @Test
    void toLowerCaseRootUsesRootLocale() {
        // Turkish locale would uppercase 'i' to 'İ' but ROOT locale must not
        assertThat(StringNormalizer.toLowerCaseRoot("TITLE")).isEqualTo("title");
    }
}
