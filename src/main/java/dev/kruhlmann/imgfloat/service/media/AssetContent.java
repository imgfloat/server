package dev.kruhlmann.imgfloat.service.media;

import java.util.Arrays;
import java.util.Objects;

public record AssetContent(byte[] bytes, String mediaType) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AssetContent that = (AssetContent) o;
        return Arrays.equals(bytes, that.bytes) && Objects.equals(mediaType, that.mediaType);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(mediaType);
        result = 31 * result + Arrays.hashCode(bytes);
        return result;
    }

    @Override
    public String toString() {
        return "AssetContent{" + "bytes=" + Arrays.toString(bytes) + ", mediaType='" + mediaType + '\'' + '}';
    }
}
