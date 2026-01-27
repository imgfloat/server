package dev.kruhlmann.imgfloat.model.api.request;

import jakarta.validation.constraints.NotBlank;

public class AdminRequest {

    @NotBlank
    private String username;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
