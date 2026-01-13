package dev.kruhlmann.imgfloat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "marketplace_script_hearts")
@IdClass(MarketplaceScriptHeartId.class)
public class MarketplaceScriptHeart {

    @Id
    @Column(name = "script_id")
    private String scriptId;

    @Id
    @Column(name = "username")
    private String username;

    public MarketplaceScriptHeart() {}

    public MarketplaceScriptHeart(String scriptId, String username) {
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
}
