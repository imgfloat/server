package dev.kruhlmann.imgfloat.model.api.response;

import java.util.List;

public record CopyrightReportPageView(
    List<CopyrightReportView> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {}
