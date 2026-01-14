package dev.kruhlmann.imgfloat.model;

public class ChannelScriptSettingsRequest {

    private boolean allowChannelEmotesForAssets = true;
    private boolean allowSevenTvEmotesForAssets = true;
    private boolean allowScriptChatAccess = true;

    public ChannelScriptSettingsRequest() {}

    public ChannelScriptSettingsRequest(
        boolean allowChannelEmotesForAssets,
        boolean allowSevenTvEmotesForAssets,
        boolean allowScriptChatAccess
    ) {
        this.allowChannelEmotesForAssets = allowChannelEmotesForAssets;
        this.allowSevenTvEmotesForAssets = allowSevenTvEmotesForAssets;
        this.allowScriptChatAccess = allowScriptChatAccess;
    }

    public boolean isAllowChannelEmotesForAssets() {
        return allowChannelEmotesForAssets;
    }

    public void setAllowChannelEmotesForAssets(boolean allowChannelEmotesForAssets) {
        this.allowChannelEmotesForAssets = allowChannelEmotesForAssets;
    }

    public boolean isAllowSevenTvEmotesForAssets() {
        return allowSevenTvEmotesForAssets;
    }

    public void setAllowSevenTvEmotesForAssets(boolean allowSevenTvEmotesForAssets) {
        this.allowSevenTvEmotesForAssets = allowSevenTvEmotesForAssets;
    }

    public boolean isAllowScriptChatAccess() {
        return allowScriptChatAccess;
    }

    public void setAllowScriptChatAccess(boolean allowScriptChatAccess) {
        this.allowScriptChatAccess = allowScriptChatAccess;
    }
}
