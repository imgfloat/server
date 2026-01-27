package dev.kruhlmann.imgfloat.model.api.response;

public record ErrorResponse(int status, String message, String path) {}
