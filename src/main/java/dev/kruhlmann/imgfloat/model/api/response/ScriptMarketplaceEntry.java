package dev.kruhlmann.imgfloat.model.api.response;

public record ScriptMarketplaceEntry(
    String id,
    String name,
    String description,
    String logoUrl,
    String broadcaster,
    java.util.List<String> allowedDomains,
    long heartsCount,
    boolean hearted
) {}
