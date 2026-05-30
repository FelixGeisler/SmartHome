package org.example.mcp.dto;

public record FloorView(
        Long id,
        String name,
        int sortOrder,
        int roomCount
) {}
