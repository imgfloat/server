package dev.kruhlmann.imgfloat.model.api.request;

public record ActivePlaylistRequest(
    String playlistId   // nullable — null means deselect
) {}
