package org.felixgeisler.smarthome.device;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** REST API for listing, registering, and controlling devices. */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

  private final DeviceService service;

  /**
   * Creates the controller.
   *
   * @param service the device service
   */
  public DeviceController(DeviceService service) {
    this.service = service;
  }

  /**
   * Lists all devices.
   *
   * @return every registered device as a response view
   */
  @GetMapping
  public List<DeviceResponse> list() {
    return service.getAllDevices().stream().map(DeviceResponse::from).toList();
  }

  /**
   * Registers a device.
   *
   * @param request the device to register
   * @return the persisted device as a response view
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public DeviceResponse register(@Valid @RequestBody DeviceRegistrationRequest request) {
    Device device =
        service.register(
            request.externalId(), request.name(), request.type(), request.adapterType());
    return DeviceResponse.from(device);
  }

  /**
   * Toggles a device on or off.
   *
   * @param id the device id
   * @return the updated device as a response view
   */
  @PostMapping("/{id}/toggle")
  public DeviceResponse toggle(@PathVariable Long id) {
    return DeviceResponse.from(service.toggle(id));
  }
}
