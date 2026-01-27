package dev.kruhlmann.imgfloat.service.media;

import jakarta.validation.constraints.NotNull;

import java.util.Arrays;
import java.util.Objects;

public record OptimizedAsset(byte[] bytes, String mediaType, int width, int height, byte[] previewBytes) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OptimizedAsset that = (OptimizedAsset) o;
        return (
            width == that.width &&
            height == that.height &&
            Arrays.equals(bytes, that.bytes) &&
            Arrays.equals(previewBytes, that.previewBytes) &&
            Objects.equals(mediaType, that.mediaType)
        );
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(mediaType, width, height);
        result = 31 * result + Arrays.hashCode(bytes);
        result = 31 * result + Arrays.hashCode(previewBytes);
        return result;
    }

    @NotNull
    @Override
    public String toString() {
        return (
            "OptimizedAsset{" +
            "bytes=" +
            Arrays.toString(bytes) +
            ", mediaType='" +
            mediaType +
            '\'' +
            ", width=" +
            width +
            ", height=" +
            height +
            ", previewBytes=" +
            Arrays.toString(previewBytes) +
            '}'
        );
    }
}
