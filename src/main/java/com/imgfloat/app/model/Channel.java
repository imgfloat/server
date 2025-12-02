package com.imgfloat.app.model;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Channel {
    private final String broadcaster;
    private final Set<String> admins;
    private final Map<String, Asset> assets;

    public Channel(String broadcaster) {
        this.broadcaster = broadcaster.toLowerCase();
        this.admins = ConcurrentHashMap.newKeySet();
        this.assets = new ConcurrentHashMap<>();
    }

    public String getBroadcaster() {
        return broadcaster;
    }

    public Set<String> getAdmins() {
        return Collections.unmodifiableSet(admins);
    }

    public Map<String, Asset> getAssets() {
        return assets;
    }

    public boolean addAdmin(String username) {
        return admins.add(username.toLowerCase());
    }

    public boolean removeAdmin(String username) {
        return admins.remove(username.toLowerCase());
    }
}
