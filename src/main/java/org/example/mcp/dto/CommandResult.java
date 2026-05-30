package org.example.mcp.dto;

/**
 * Outcome of a single device command issued through MCP.
 * {@code message} is a human-readable summary the LLM can pass to the user verbatim.
 */
public record CommandResult(boolean success, Long deviceId, String deviceName, String message) {

    public static CommandResult success(Long id, String name, String message) {
        return new CommandResult(true, id, name, message);
    }

    public static CommandResult failure(Long id, String name, String message) {
        return new CommandResult(false, id, name, message);
    }
}
