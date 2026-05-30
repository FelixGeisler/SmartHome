package org.example.mcp.dto;

public record SceneView(
        Long id,
        String name,
        String icon,
        int actionCount
) {}
