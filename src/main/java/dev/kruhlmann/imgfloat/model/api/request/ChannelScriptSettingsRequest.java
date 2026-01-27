package dev.kruhlmann.imgfloat.model.api.request;

public class ChannelScriptSettingsRequest {

    private boolean allowChannelEmotesForAssets = true;
    private boolean allowSevenTvEmotesForAssets = true;
    private boolean allowScriptChatAccess = true;

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

    public boolean isAllowSevenTvEmotesForAssets() {
        return allowSevenTvEmotesForAssets;
    }

    public boolean isAllowScriptChatAccess() {
        return allowScriptChatAccess;
    }

}
