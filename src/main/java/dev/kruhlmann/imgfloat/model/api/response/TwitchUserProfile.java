package dev.kruhlmann.imgfloat.model.api.response;

/**
 * Minimal Twitch user details used for rendering avatars and display names.
 */
public record TwitchUserProfile(String login, String displayName, String avatarUrl) {
    public TwitchUserProfile {
        if (displayName == null || displayName.isBlank()) {
            displayName = login;
        }
    }
}
