package dev.kruhlmann.imgfloat.service.media;

public record OptimizedAsset(byte[] bytes, String mediaType, int width, int height, byte[] previewBytes) { }
