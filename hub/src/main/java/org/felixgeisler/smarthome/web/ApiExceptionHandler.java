package org.felixgeisler.smarthome.web;

import org.felixgeisler.smarthome.assistant.AssistantException;
import org.felixgeisler.smarthome.automation.AutomationNotFoundException;
import org.felixgeisler.smarthome.dashboard.DashboardLayoutException;
import org.felixgeisler.smarthome.device.DeviceAlreadyExistsException;
import org.felixgeisler.smarthome.device.DeviceNotFoundException;
import org.felixgeisler.smarthome.device.InvalidCommandException;
import org.felixgeisler.smarthome.device.UnsupportedAdapterTypeException;
import org.felixgeisler.smarthome.device.UnsupportedCapabilityException;
import org.felixgeisler.smarthome.floor.FloorAlreadyExistsException;
import org.felixgeisler.smarthome.floor.FloorNotFoundException;
import org.felixgeisler.smarthome.integration.UnknownAdapterException;
import org.felixgeisler.smarthome.integration.homematic.HomematicCcuException;
import org.felixgeisler.smarthome.integration.hue.HueBridgeException;
import org.felixgeisler.smarthome.room.RoomAlreadyExistsException;
import org.felixgeisler.smarthome.room.RoomLayoutException;
import org.felixgeisler.smarthome.room.RoomNotFoundException;
import org.felixgeisler.smarthome.security.AuthAlreadyConfiguredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps domain exceptions to RFC 9457 problem responses with consistent statuses and bodies. */
@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(DeviceNotFoundException.class)
  ProblemDetail handleDeviceNotFound(DeviceNotFoundException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(DeviceAlreadyExistsException.class)
  ProblemDetail handleDeviceAlreadyExists(DeviceAlreadyExistsException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
  }

  @ExceptionHandler(RoomNotFoundException.class)
  ProblemDetail handleRoomNotFound(RoomNotFoundException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(RoomAlreadyExistsException.class)
  ProblemDetail handleRoomAlreadyExists(RoomAlreadyExistsException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
  }

  @ExceptionHandler(FloorNotFoundException.class)
  ProblemDetail handleFloorNotFound(FloorNotFoundException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(FloorAlreadyExistsException.class)
  ProblemDetail handleFloorAlreadyExists(FloorAlreadyExistsException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
  }

  @ExceptionHandler(RoomLayoutException.class)
  ProblemDetail handleRoomLayout(RoomLayoutException ex) {
    // The room layout is too large to store.
    return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
  }

  @ExceptionHandler(AutomationNotFoundException.class)
  ProblemDetail handleAutomationNotFound(AutomationNotFoundException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(UnsupportedAdapterTypeException.class)
  ProblemDetail handleUnsupportedAdapterType(UnsupportedAdapterTypeException ex) {
    // No such integration configured on this hub.
    return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
  }

  @ExceptionHandler(UnsupportedCapabilityException.class)
  ProblemDetail handleUnsupportedCapability(UnsupportedCapabilityException ex) {
    // The device cannot perform this command.
    return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
  }

  @ExceptionHandler(InvalidCommandException.class)
  ProblemDetail handleInvalidCommand(InvalidCommandException ex) {
    // The command is malformed (empty, out of range, or color plus color temperature).
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  @ExceptionHandler(DashboardLayoutException.class)
  ProblemDetail handleDashboardLayout(DashboardLayoutException ex) {
    // The dashboard layout is too large to store.
    return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
  }

  @ExceptionHandler(UnknownAdapterException.class)
  ProblemDetail handleUnknownAdapter(UnknownAdapterException ex) {
    // Data integrity: a device has an adapter type no adapter handles. Log the message (not a
    // stack trace) and keep it out of the 5xx body to avoid leaking internals.
    String reason = ex.getMessage();
    log.error("No adapter registered for a device toggle: {}", reason);
    return ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @ExceptionHandler(HueBridgeException.class)
  ProblemDetail handleHueBridge(HueBridgeException ex) {
    // The hub could not reach or use the Hue bridge.
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
  }

  @ExceptionHandler(HomematicCcuException.class)
  ProblemDetail handleHomematicCcu(HomematicCcuException ex) {
    // The hub could not reach or use the Homematic CCU.
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
  }

  @ExceptionHandler(AuthAlreadyConfiguredException.class)
  ProblemDetail handleAuthAlreadyConfigured(AuthAlreadyConfiguredException ex) {
    // First-run setup ran but an administrator already exists.
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
  }

  @ExceptionHandler(AssistantException.class)
  ProblemDetail handleAssistant(AssistantException ex) {
    // Claude API is an upstream service, so surface failures as a bad gateway; the message is
    // operator-actionable, so pass it through.
    String reason = ex.getMessage();
    log.warn("Assistant request failed: {}", reason);
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, reason);
  }
}
