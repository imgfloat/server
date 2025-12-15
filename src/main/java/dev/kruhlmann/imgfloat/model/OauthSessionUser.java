package dev.kruhlmann.imgfloat.model;

import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

public record OauthSessionUser(String login, String displayName) {
    public static OauthSessionUser from(OAuth2AuthenticationToken authentication) {
        if (authentication == null) {
            return null;
        }
        String login = authentication.getPrincipal().<String>getAttribute("preferred_username");
        if (login == null) {
            login = authentication.getPrincipal().<String>getAttribute("login");
        }
        if (login == null) {
            login = authentication.getPrincipal().getName();
        }
        String displayName = authentication.getPrincipal().<String>getAttribute("display_name");
        if (displayName == null) {
            displayName = login;
        }
        return new OauthSessionUser(login, displayName);
    }
}
