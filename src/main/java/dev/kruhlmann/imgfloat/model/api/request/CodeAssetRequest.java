package dev.kruhlmann.imgfloat.model.api.request;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;

public class CodeAssetRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String source;

    private String description;

    @Nullable
    private Boolean isPublic;

    private java.util.List<String> allowedDomains;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Nullable
    public Boolean getIsPublic() {
        return isPublic;
    }

    public java.util.List<String> getAllowedDomains() {
        return allowedDomains;
    }

    public void setAllowedDomains(java.util.List<String> allowedDomains) {
        this.allowedDomains = allowedDomains;
    }

    public void setPublic(@Nullable Boolean aPublic) {
        isPublic = aPublic;
    }
}
