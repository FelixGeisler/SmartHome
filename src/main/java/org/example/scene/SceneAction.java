package org.example.scene;

/**
 * One step of a scene — sends {@code payloadJson} (a JSON-string command, e.g.
 * {@code {"on":true,"brightness":80}}) to the device identified by {@code deviceId}.
 * Persisted as part of {@link Scene#getActionsJson()}.
 */
public record SceneAction(long deviceId, String payloadJson) {}
