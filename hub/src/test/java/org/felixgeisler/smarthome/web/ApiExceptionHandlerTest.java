package org.felixgeisler.smarthome.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.felixgeisler.smarthome.device.Capability;
import org.felixgeisler.smarthome.device.DeviceAlreadyExistsException;
import org.felixgeisler.smarthome.device.DeviceNotFoundException;
import org.felixgeisler.smarthome.device.InvalidCommandException;
import org.felixgeisler.smarthome.device.UnsupportedAdapterTypeException;
import org.felixgeisler.smarthome.device.UnsupportedCapabilityException;
import org.felixgeisler.smarthome.integration.UnknownAdapterException;
import org.felixgeisler.smarthome.integration.hue.HueBridgeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class ApiExceptionHandlerTest {

  private final ApiExceptionHandler handler = new ApiExceptionHandler();

  @DisplayName("a missing device maps to 404 with its message")
  @Test
  void deviceNotFound_mapsToNotFound() {
    DeviceNotFoundException ex = new DeviceNotFoundException(99L);

    ProblemDetail problem = handler.handleDeviceNotFound(ex);

    assertEquals(HttpStatus.NOT_FOUND.value(), problem.getStatus());
    assertEquals(ex.getMessage(), problem.getDetail());
  }

  @DisplayName("a duplicate device maps to 409 with its message")
  @Test
  void deviceAlreadyExists_mapsToConflict() {
    DeviceAlreadyExistsException ex = new DeviceAlreadyExistsException("ext-1");

    ProblemDetail problem = handler.handleDeviceAlreadyExists(ex);

    assertEquals(HttpStatus.CONFLICT.value(), problem.getStatus());
    assertEquals(ex.getMessage(), problem.getDetail());
  }

  @DisplayName("an unsupported adapter type maps to 422 with its message")
  @Test
  void unsupportedAdapterType_mapsToUnprocessable() {
    UnsupportedAdapterTypeException ex = new UnsupportedAdapterTypeException("nest");

    ProblemDetail problem = handler.handleUnsupportedAdapterType(ex);

    assertEquals(HttpStatus.UNPROCESSABLE_CONTENT.value(), problem.getStatus());
    assertEquals(ex.getMessage(), problem.getDetail());
  }

  @DisplayName("an unsupported capability maps to 422 with its message")
  @Test
  void unsupportedCapability_mapsToUnprocessable() {
    UnsupportedCapabilityException ex =
        new UnsupportedCapabilityException(1L, Capability.SWITCHABLE);

    ProblemDetail problem = handler.handleUnsupportedCapability(ex);

    assertEquals(HttpStatus.UNPROCESSABLE_CONTENT.value(), problem.getStatus());
    assertEquals(ex.getMessage(), problem.getDetail());
  }

  @DisplayName("an invalid command maps to 400 with its message")
  @Test
  void invalidCommand_mapsToBadRequest() {
    InvalidCommandException ex = new InvalidCommandException("Command sets no attributes");

    ProblemDetail problem = handler.handleInvalidCommand(ex);

    assertEquals(HttpStatus.BAD_REQUEST.value(), problem.getStatus());
    assertEquals(ex.getMessage(), problem.getDetail());
  }

  @DisplayName("an unknown adapter maps to 500 without leaking a detail")
  @Test
  void unknownAdapter_mapsToServerErrorWithoutDetail() {
    UnknownAdapterException ex = new UnknownAdapterException("mqtt");

    ProblemDetail problem = handler.handleUnknownAdapter(ex);

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), problem.getStatus());
    assertNull(problem.getDetail());
  }

  @DisplayName("a Hue bridge failure maps to 502 with its message")
  @Test
  void hueBridge_mapsToBadGateway() {
    HueBridgeException ex = new HueBridgeException("Could not reach the Hue bridge");

    ProblemDetail problem = handler.handleHueBridge(ex);

    assertEquals(HttpStatus.BAD_GATEWAY.value(), problem.getStatus());
    assertEquals(ex.getMessage(), problem.getDetail());
  }
}
