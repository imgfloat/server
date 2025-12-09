package com.imgfloat.app.model;

import jakarta.validation.constraints.Positive;

public class CanvasSettingsRequest {
    @Positive
    private double width;

    @Positive
    private double height;

    public CanvasSettingsRequest() {
    }

    public CanvasSettingsRequest(double width, double height) {
        this.width = width;
        this.height = height;
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = width;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
    }
}
