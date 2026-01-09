package dev.kruhlmann.imgfloat.model;

public record ScriptAssetAttachmentView(
    String id,
    String scriptAssetId,
    String name,
    String url,
    String mediaType,
    String originalMediaType,
    AssetType assetType
) {
    public static ScriptAssetAttachmentView fromAttachment(String broadcaster, ScriptAssetAttachment attachment) {
        if (attachment == null) {
            return null;
        }
        return new ScriptAssetAttachmentView(
            attachment.getId(),
            attachment.getScriptAssetId(),
            attachment.getName(),
            "/api/channels/" + broadcaster + "/script-assets/" + attachment.getScriptAssetId() + "/attachments/" +
            attachment.getId() +
            "/content",
            attachment.getMediaType(),
            attachment.getOriginalMediaType(),
            attachment.getAssetType()
        );
    }
}
