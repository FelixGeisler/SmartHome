package org.felixgeisler.smarthome.device;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

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
   * Returns a single device.
   *
   * @param id the device id
   * @return the device as a response view
   */
  @GetMapping("/{id}")
  public DeviceResponse get(@PathVariable Long id) {
    return DeviceResponse.from(service.getById(id));
  }

  /**
   * Registers a new device.
   *
   * @param request the device to register
   * @param uriBuilder builder for the created resource's location
   * @return 201 with the persisted device and its {@code Location}
   */
  @PostMapping
  public ResponseEntity<DeviceResponse> register(
      @Valid @RequestBody DeviceRegistrationRequest request, UriComponentsBuilder uriBuilder) {
    Device device =
        service.register(
            request.externalId(), request.name(), request.type(), request.adapterType());
    URI location = uriBuilder.path("/api/devices/{id}").buildAndExpand(device.getId()).toUri();
    return ResponseEntity.created(location).body(DeviceResponse.from(device));
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
