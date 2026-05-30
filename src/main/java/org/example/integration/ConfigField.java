package org.example.integration;

/** Describes a single configuration field for an adapter type — used to render the UI form. */
public record ConfigField(
        String key,
        String label,
        String type,         // "text" | "password" | "number"
        boolean required,
        String placeholder,
        String description
) {}
