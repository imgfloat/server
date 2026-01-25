package dev.kruhlmann.imgfloat.service.media;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class MediaTypeRegistry {

    private static final Map<String, String> EXTENSION_TO_MEDIA_TYPE = buildExtensionMap();
    private static final Map<String, String> MEDIA_TYPE_TO_EXTENSION = buildMediaTypeMap();
    private static final Set<String> SUPPORTED_MEDIA_TYPES = Set.copyOf(MEDIA_TYPE_TO_EXTENSION.keySet());

    private MediaTypeRegistry() {}

    public static Optional<String> mediaTypeForExtension(String extension) {
        if (extension == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(EXTENSION_TO_MEDIA_TYPE.get(extension.toLowerCase(Locale.ROOT)));
    }

    public static Optional<String> extensionForMediaType(String mediaType) {
        if (mediaType == null) {
            return Optional.empty();
        }
        String normalized = normalizeJavaScriptMediaType(mediaType).toLowerCase(Locale.ROOT);
        return Optional.ofNullable(MEDIA_TYPE_TO_EXTENSION.get(normalized));
    }

    public static boolean isSupportedMediaType(String mediaType) {
        if (mediaType == null || mediaType.isBlank()) {
            return false;
        }
        String normalized = normalizeJavaScriptMediaType(mediaType).toLowerCase(Locale.ROOT);
        return SUPPORTED_MEDIA_TYPES.contains(normalized);
    }

    public static List<String> supportedMediaTypes() {
        return SUPPORTED_MEDIA_TYPES.stream().sorted().toList();
    }

    public static String supportedMediaTypesSummary() {
        return String.join(", ", supportedMediaTypes());
    }

    public static String normalizeJavaScriptMediaType(String mediaType) {
        if (mediaType == null) {
            return null;
        }
        String normalized = mediaType.toLowerCase(Locale.ROOT);
        if (normalized.contains("javascript") || normalized.contains("ecmascript")) {
            return "application/javascript";
        }
        return mediaType;
    }

    private static Map<String, String> buildExtensionMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("png", "image/png");
        map.put("apng", "image/apng");
        map.put("jpg", "image/jpeg");
        map.put("jpeg", "image/jpeg");
        map.put("gif", "image/gif");
        map.put("webp", "image/webp");
        map.put("svg", "image/svg+xml");
        map.put("bmp", "image/bmp");
        map.put("tiff", "image/tiff");
        map.put("mp4", "video/mp4");
        map.put("webm", "video/webm");
        map.put("mov", "video/quicktime");
        map.put("mkv", "video/x-matroska");
        map.put("mp3", "audio/mpeg");
        map.put("wav", "audio/wav");
        map.put("ogg", "audio/ogg");
        map.put("flac", "audio/flac");
        map.put("ttf", "font/ttf");
        map.put("otf", "font/otf");
        map.put("woff", "font/woff");
        map.put("woff2", "font/woff2");
        map.put("glb", "model/gltf-binary");
        map.put("gltf", "model/gltf+json");
        map.put("obj", "model/obj");
        map.put("js", "application/javascript");
        map.put("mjs", "text/javascript");
        return Map.copyOf(map);
    }

    private static Map<String, String> buildMediaTypeMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("image/png", ".png");
        map.put("image/apng", ".apng");
        map.put("image/jpeg", ".jpg");
        map.put("image/jpg", ".jpg");
        map.put("image/gif", ".gif");
        map.put("image/webp", ".webp");
        map.put("image/svg+xml", ".svg");
        map.put("image/bmp", ".bmp");
        map.put("image/tiff", ".tiff");
        map.put("video/mp4", ".mp4");
        map.put("video/webm", ".webm");
        map.put("video/quicktime", ".mov");
        map.put("video/x-matroska", ".mkv");
        map.put("audio/mpeg", ".mp3");
        map.put("audio/mp3", ".mp3");
        map.put("audio/wav", ".wav");
        map.put("audio/ogg", ".ogg");
        map.put("audio/webm", ".webm");
        map.put("audio/flac", ".flac");
        map.put("font/ttf", ".ttf");
        map.put("font/otf", ".otf");
        map.put("font/woff", ".woff");
        map.put("font/woff2", ".woff2");
        map.put("model/gltf-binary", ".glb");
        map.put("model/gltf+json", ".gltf");
        map.put("model/obj", ".obj");
        map.put("application/javascript", ".js");
        map.put("text/javascript", ".js");
        return Map.copyOf(map);
    }
}
