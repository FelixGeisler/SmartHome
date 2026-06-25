package org.felixgeisler.smarthome.web;

import org.felixgeisler.smarthome.device.DeviceAlreadyExistsException;
import org.felixgeisler.smarthome.device.DeviceNotFoundException;
import org.felixgeisler.smarthome.device.InvalidCommandException;
import org.felixgeisler.smarthome.device.UnsupportedAdapterTypeException;
import org.felixgeisler.smarthome.device.UnsupportedCapabilityException;
import org.felixgeisler.smarthome.integration.UnknownAdapterException;
import org.felixgeisler.smarthome.integration.hue.HueBridgeException;
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

  @ExceptionHandler(UnsupportedAdapterTypeException.class)
  ProblemDetail handleUnsupportedAdapterType(UnsupportedAdapterTypeException ex) {
    // Client-actionable at registration time: this hub has no such integration configured.
    return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
  }

  @ExceptionHandler(UnsupportedCapabilityException.class)
  ProblemDetail handleUnsupportedCapability(UnsupportedCapabilityException ex) {
    // Client-actionable: the command addressed a device that cannot perform it.
    return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
  }

  @ExceptionHandler(InvalidCommandException.class)
  ProblemDetail handleInvalidCommand(InvalidCommandException ex) {
    // Client-actionable: the command is malformed for the neutral contract (empty, out of range,
    // or setting color and color temperature together).
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  @ExceptionHandler(UnknownAdapterException.class)
  ProblemDetail handleUnknownAdapter(UnknownAdapterException ex) {
    // Server-side data integrity: a device was persisted with an adapter type no adapter handles.
    // The condition is well-defined, so log the message (not a full stack trace) and keep it out
    // of the client response (don't leak internals in 5xx).
    String reason = ex.getMessage();
    log.error("No adapter registered for a device toggle: {}", reason);
    return ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @ExceptionHandler(HueBridgeException.class)
  ProblemDetail handleHueBridge(HueBridgeException ex) {
    // Client-actionable: the hub could not reach or use the Hue bridge (unreachable, not paired).
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
  }
}
