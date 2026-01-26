package dev.kruhlmann.imgfloat.model;

public record ErrorResponse(int status, String message, String path) {}
