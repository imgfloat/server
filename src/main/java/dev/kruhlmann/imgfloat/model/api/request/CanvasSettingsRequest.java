package dev.kruhlmann.imgfloat.model.api.request;

import jakarta.validation.constraints.Positive;

public class CanvasSettingsRequest {

    @Positive
    private final double width;

    @Positive
    private final double height;

    public CanvasSettingsRequest(double width, double height) {
        this.width = width;
        this.height = height;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }

}
