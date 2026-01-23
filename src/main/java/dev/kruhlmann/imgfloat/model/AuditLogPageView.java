package dev.kruhlmann.imgfloat.model;

import java.util.List;

public record AuditLogPageView(
    List<AuditLogEntryView> entries,
    int page,
    int size,
    long totalElements,
    int totalPages
) {}
