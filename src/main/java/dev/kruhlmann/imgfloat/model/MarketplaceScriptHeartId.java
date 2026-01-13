package dev.kruhlmann.imgfloat.model;

import java.io.Serializable;
import java.util.Objects;

public class MarketplaceScriptHeartId implements Serializable {

    private String scriptId;
    private String username;

    public MarketplaceScriptHeartId() {}

    public MarketplaceScriptHeartId(String scriptId, String username) {
        this.scriptId = scriptId;
        this.username = username;
    }

    public String getScriptId() {
        return scriptId;
    }

    public void setScriptId(String scriptId) {
        this.scriptId = scriptId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        MarketplaceScriptHeartId that = (MarketplaceScriptHeartId) other;
        return Objects.equals(scriptId, that.scriptId) && Objects.equals(username, that.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scriptId, username);
    }
}
