package com.imgfloat.app.service.media;

public record OptimizedAsset(byte[] bytes, String mediaType, int width, int height, byte[] previewBytes) { }
