package com.imgfloat.app.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class AssetRequest {
    @NotBlank
    private String url;

    @Min(1)
    private double width;

    @Min(1)
    private double height;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
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
