package dev.kruhlmann.imgfloat.model.api.request;

import jakarta.validation.constraints.NotBlank;

public class ScriptMarketplaceImportRequest {

    @NotBlank
    private String targetBroadcaster;

    public String getTargetBroadcaster() {
        return targetBroadcaster;
    }

    public void setTargetBroadcaster(String targetBroadcaster) {
        this.targetBroadcaster = targetBroadcaster;
    }
}
