package dev.kruhlmann.imgfloat.model.api.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;

public class CanvasSettingsRequest {

    @Positive
    private final double width;

    @Positive
    private final double height;

    @DecimalMin(value = "-60.0")
    @DecimalMax(value = "0.0")
    private final Double maxVolumeDb;

    public CanvasSettingsRequest(double width, double height, Double maxVolumeDb) {
        this.width = width;
        this.height = height;
        this.maxVolumeDb = maxVolumeDb;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }

    public Double getMaxVolumeDb() {
        return maxVolumeDb;
    }

}
