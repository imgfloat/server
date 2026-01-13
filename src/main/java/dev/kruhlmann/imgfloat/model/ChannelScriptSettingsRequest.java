package dev.kruhlmann.imgfloat.model;

public class ChannelScriptSettingsRequest {

    private boolean allowChannelEmotesForAssets = true;
    private boolean allowScriptChatAccess = true;

    public ChannelScriptSettingsRequest() {}

    public ChannelScriptSettingsRequest(boolean allowChannelEmotesForAssets, boolean allowScriptChatAccess) {
        this.allowChannelEmotesForAssets = allowChannelEmotesForAssets;
        this.allowScriptChatAccess = allowScriptChatAccess;
    }

    public boolean isAllowChannelEmotesForAssets() {
        return allowChannelEmotesForAssets;
    }

    public void setAllowChannelEmotesForAssets(boolean allowChannelEmotesForAssets) {
        this.allowChannelEmotesForAssets = allowChannelEmotesForAssets;
    }

    public boolean isAllowScriptChatAccess() {
        return allowScriptChatAccess;
    }

    public void setAllowScriptChatAccess(boolean allowScriptChatAccess) {
        this.allowScriptChatAccess = allowScriptChatAccess;
    }
}
