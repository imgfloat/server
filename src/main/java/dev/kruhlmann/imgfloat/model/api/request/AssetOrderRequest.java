package dev.kruhlmann.imgfloat.model.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class AssetOrderRequest {

    @Valid
    @NotEmpty
    private List<AssetOrderUpdate> updates;

    public List<AssetOrderUpdate> getUpdates() {
        return updates;
    }

    public void setUpdates(List<AssetOrderUpdate> updates) {
        this.updates = updates;
    }

    public static record AssetOrderUpdate(
        @NotBlank
        String assetId,
        @NotNull
        Integer order
    ) {}
}
