package dev.kruhlmann.imgfloat.model;

import java.util.Locale;

public enum AssetType {
    IMAGE,
    VIDEO,
    AUDIO,
    MODEL,
    SCRIPT,
    OTHER;

    public static AssetType fromMediaType(String mediaType, String originalMediaType) {
        String raw = mediaType != null && !mediaType.isBlank() ? mediaType : originalMediaType;
        if (raw == null || raw.isBlank()) {
            return OTHER;
        }
        String normalized = raw.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("image/")) {
            return IMAGE;
        }
        if (normalized.startsWith("video/")) {
            return VIDEO;
        }
        if (normalized.startsWith("audio/")) {
            return AUDIO;
        }
        if (normalized.startsWith("model/")) {
            return MODEL;
        }
        if (normalized.startsWith("application/javascript") || normalized.startsWith("text/javascript")) {
            return SCRIPT;
        }
        return OTHER;
    }
}
