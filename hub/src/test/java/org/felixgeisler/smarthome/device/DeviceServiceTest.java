package org.felixgeisler.smarthome.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.felixgeisler.smarthome.capability.Capability;
import org.felixgeisler.smarthome.capability.XyColor;
import org.felixgeisler.smarthome.integration.DeviceAdapter;
import org.felixgeisler.smarthome.integration.DeviceAdapterRegistry;
import org.felixgeisler.smarthome.room.Room;
import org.felixgeisler.smarthome.room.RoomNotFoundException;
import org.felixgeisler.smarthome.room.RoomRemoved;
import org.felixgeisler.smarthome.room.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

  private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

  @Mock private DeviceRepository devices;
  @Mock private DeviceAdapterRegistry adapters;
  @Mock private DeviceAdapter adapter;
  @Mock private ApplicationEventPublisher events;
  @Mock private RoomRepository rooms;
  @Captor private ArgumentCaptor<Map<String, Object>> commandCaptor;

  private DeviceService service;

  @BeforeEach
  void setUp() {
    service = new DeviceService(devices, adapters, events, Clock.fixed(NOW, ZoneOffset.UTC), rooms);
  }

  @DisplayName("toggle() switches an off device on")
  @Test
  void toggle_switchesAnOffDeviceOn() {
    Device device = new Device("ext-1", "Plug", DeviceType.SHELLY_PLUG, "shelly");
    when(devices.findById(1L)).thenReturn(Optional.of(device));
    when(adapters.get("shelly")).thenReturn(adapter);
    when(devices.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Device result = service.toggle(1L);

    verify(adapter).sendCommand(eq("ext-1"), commandCaptor.capture());
    assertEquals(true, commandCaptor.getValue().get("on"));
    assertEquals("true", result.getState().get("on"));
    verify(devices).save(device);
  }

  @DisplayName("applyReachability() re-reads the device and persists a flipped flag")
  @Test
  void applyReachability_persistsOnChange() {
    Device device = new Device("ext-1", "Plug", DeviceType.SHELLY_PLUG, "shelly");
    when(devices.findById(1L)).thenReturn(Optional.of(device));
    when(devices.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.applyReachability(1L, false);

    assertFalse(device.isReachable());
    verify(devices).save(device);
  }

  @DisplayName("applyReachability() does nothing when the flag is unchanged")
  @Test
  void applyReachability_noOpWhenUnchanged() {
    Device device = new Device("ext-1", "Plug", DeviceType.SHELLY_PLUG, "shelly");
    when(devices.findById(1L)).thenReturn(Optional.of(device));

    service.applyReachability(1L, true);

    verify(devices, never()).save(any(Device.class));
  }

  @DisplayName("applyReachability() ignores a device deleted during the sweep")
  @Test
  void applyReachability_ignoresMissingDevice() {
    when(devices.findById(99L)).thenReturn(Optional.empty());

    service.applyReachability(99L, false);

    verify(devices, never()).save(any(Device.class));
  }

  @DisplayName("refreshReportingReachability() marks a device silent past the cutoff offline")
  @Test
  void refreshReportingReachability_marksStaleDeviceOffline() {
    Device device = new Device("node-1", "Node", DeviceType.SENSOR_NODE, null);
    device.markSeen(NOW.minus(Duration.ofMinutes(20)));
    when(devices.findById(1L)).thenReturn(Optional.of(device));
    when(devices.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.refreshReportingReachability(1L, NOW.minus(Duration.ofMinutes(10)));

    assertFalse(device.isReachable());
    verify(devices).save(device);
  }

  @DisplayName("refreshReportingReachability() brings a device heard from within the cutoff online")
  @Test
  void refreshReportingReachability_bringsFreshDeviceOnline() {
    Device device = new Device("node-1", "Node", DeviceType.SENSOR_NODE, null);
    device.markSeen(NOW.minus(Duration.ofMinutes(2)));
    device.setReachable(false);
    when(devices.findById(1L)).thenReturn(Optional.of(device));
    when(devices.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.refreshReportingReachability(1L, NOW.minus(Duration.ofMinutes(10)));

    assertTrue(device.isReachable());
    verify(devices).save(device);
  }

  @DisplayName("refreshReportingReachability() ignores a device deleted during the sweep")
  @Test
  void refreshReportingReachability_ignoresMissingDevice() {
    when(devices.findById(99L)).thenReturn(Optional.empty());

    service.refreshReportingReachability(99L, NOW);

    verify(devices, never()).save(any(Device.class));
  }

  @DisplayName("syncState() folds a device's reported state in and pushes when it changed")
  @Test
  void syncState_updatesChangedStateAndPushes() {
    Device device = new Device("ext-1", "Plug", DeviceType.SHELLY_PLUG, "shelly");
    device.putState("on", "false");
    when(devices.findById(1L)).thenReturn(Optional.of(device));
    when(devices.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.syncState(1L, Map.of("on", true));

    assertEquals("true", device.getState().get("on"));
    verify(devices).save(device);
  }

  @DisplayName("syncState() does nothing when the reported state matches the stored state")
  @Test
  void syncState_noOpWhenUnchanged() {
    Device device = new Device("ext-1", "Plug", DeviceType.SHELLY_PLUG, "shelly");
    device.putState("on", "true");
    when(devices.findById(1L)).thenReturn(Optional.of(device));

    service.syncState(1L, Map.of("on", true));

    verify(devices, never()).save(any(Device.class));
  }

  @DisplayName("syncState() ignores a device deleted during the poll")
  @Test
  void syncState_ignoresMissingDevice() {
    when(devices.findById(99L)).thenReturn(Optional.empty());

    service.syncState(99L, Map.of("on", true));

    verify(devices, never()).save(any(Device.class));
  }

  @DisplayName("recordReading() marks a device seen and brings it back online")
  @Test
  void recordReading_marksTheDeviceSeen() {
    Device device = new Device("node-1", "Climate", DeviceType.SENSOR_NODE, null);
    device.addSensor("temperature", SensorType.TEMPERATURE, "°C");
    device.setReachable(false);
    when(devices.findByExternalId("node-1")).thenReturn(Optional.of(device));
    when(devices.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.recordReading("node-1", "temperature", "21");

    assertTrue(device.isReachable());
    assertEquals(NOW, device.getLastSeenAt());
  }

  @DisplayName("toggle() switches an on device off")
  @Test
  void toggle_switchesAnOnDeviceOff() {
    Device device = new Device("ext-1", "Plug", DeviceType.SHELLY_PLUG, "shelly");
    device.putState("on", "true");
    when(devices.findById(1L)).thenReturn(Optional.of(device));
    when(adapters.get("shelly")).thenReturn(adapter);
    when(devices.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Device result = service.toggle(1L);

    verify(adapter).sendCommand(eq("ext-1"), commandCaptor.capture());
    assertEquals(false, commandCaptor.getValue().get("on"));
    assertEquals("false", result.getState().get("on"));
  }

  @DisplayName("toggle() throws when the device does not exist")
  @Test
  void toggle_throwsWhenDeviceMissing() {
    when(devices.findById(99L)).thenReturn(Optional.empty());

    assertThrows(DeviceNotFoundException.class, () -> service.toggle(99L));

    verify(devices, never()).save(any());
  }

  @DisplayName("register() saves a new device when none exists yet")
  @Test
  void register_savesNewDeviceWhenAbsent() {
    when(adapters.supports("shelly")).thenReturn(true);
    when(devices.findByExternalId("ext-1")).thenReturn(Optional.empty());
    when(devices.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Device result =
        service.register("ext-1", "Plug", DeviceType.SHELLY_PLUG, "shelly", Set.of(), List.of());

    assertEquals("ext-1", result.getExternalId());
    assertEquals("Plug", result.getName());
    assertEquals(DeviceType.SHELLY_PLUG, result.getType());
    assertEquals("shelly", result.getAdapterType());
    assertEquals(Set.of(Capability.SWITCHABLE), result.getCapabilities());
  }

  @DisplayName("register() stores the detected capabilities for rich devices")
  @Test
  void register_storesDetectedCapabilitiesForRichDevices() {
    when(adapters.supports("hue")).thenReturn(true);
    when(devices.findByExternalId("light-1")).thenReturn(Optional.empty());
    when(devices.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Device result =
        service.register(
            "light-1",
            "Lamp",
            DeviceType.HUE_LIGHT,
            "hue",
            Set.of(Capability.SWITCHABLE, Capability.DIMMABLE, Capability.COLOR),
            List.of());

    assertEquals(
        Set.of(Capability.SWITCHABLE, Capability.DIMMABLE, Capability.COLOR),
        result.getCapabilities());
  }

  @DisplayName("register() throws when the adapter type is unsupported")
  @Test
  void register_throwsWhenAdapterTypeUnsupported() {
    when(adapters.supports("nest")).thenReturn(false);

    assertThrows(
        UnsupportedAdapterTypeException.class,
        () ->
            service.register(
                "ext-1", "Plug", DeviceType.SHELLY_PLUG, "nest", Set.of(), List.of()));

    verify(devices, never()).save(any());
  }

  @DisplayName("register() throws when a device with the same external id already exists")
  @Test
  void register_throwsWhenDeviceAlreadyExists() {
    Device existing = new Device("ext-1", "Existing", DeviceType.SHELLY_PLUG, "shelly");
    when(adapters.supports("shelly")).thenReturn(true);
    when(devices.findByExternalId("ext-1")).thenReturn(Optional.of(existing));

    assertThrows(
        DeviceAlreadyExistsException.class,
        () ->
            service.register(
                "ext-1", "New", DeviceType.SHELLY_PLUG, "shelly", Set.of(), List.of()));

    verify(devices, never()).save(any());
  }

  @DisplayName("register() throws already-exists when the save hits a unique constraint")
  @Test
  void register_throwsAlreadyExistsWhenSaveHitsUniqueConstraint() {
    when(adapters.supports("shelly")).thenReturn(true);
    when(devices.findByExternalId("ext-1")).thenReturn(Optional.empty());
    when(devices.save(any(Device.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate externalId"));

    assertThrows(
        DeviceAlreadyExistsException.class,
        () ->
            service.register(
                "ext-1", "Plug", DeviceType.SHELLY_PLUG, "shelly", Set.of(), List.of()));
  }

  @DisplayName("getById() returns the matching device")
  @Test
  void getById_returnsDevice() {
    Device device = new Device("ext-1", "Plug", DeviceType.SHELLY_PLUG, "shelly");
    when(devices.findById(1L)).thenReturn(Optional.of(device));

    Device result = service.getById(1L);

    assertSame(device, result);
  }

  @DisplayName("getById() throws when the device does not exist")
  @Test
  void getById_throwsWhenDeviceMissing() {
    when(devices.findById(99L)).thenReturn(Optional.empty());

    assertThrows(DeviceNotFoundException.class, () -> service.getById(99L));
  }

  @DisplayName("getAllDevices() returns the repository contents")
  @Test
  void getAllDevices_returnsRepositoryContents() {
    Device device = new Device("ext-1", "Plug", DeviceType.SHELLY_PLUG, "shelly");
    when(devices.findAll()).thenReturn(List.of(device));

    List<Device> result = service.getAllDevices();

    assertEquals(1, result.size());
    assertSame(device, result.getFirst());
  }

  @DisplayName("register() saves a sensing device with its declared sensors and no adapter")
  @Test
  void register_savesSensingDeviceWithDeclaredSensorsAndNoAdapter() {
    when(devices.findByExternalId("node-1")).thenReturn(Optional.empty());
    when(devices.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Device result =
        service.register(
            "node-1",
            "Climate",
            DeviceType.SENSOR_NODE,
            null,
            Set.of(),
            List.of(new SensorSpec("temperature", SensorType.TEMPERATURE, "°C")));

    assertNull(result.getAdapterType());
    assertEquals(1, result.getSensors().size());
    assertEquals("temperature", result.getSensors().getFirst().getKey());
  }

  @DisplayName("recordReading() updates the matching sensor and stamps the update time")
  @Test
  void recordReading_updatesMatchingSensorWithTimestamp() {
    Device device = new Device("node-1", "Climate", DeviceType.SENSOR_NODE, null);
    device.addSensor("temperature", SensorType.TEMPERATURE, "°C");
    when(devices.findByExternalId("node-1")).thenReturn(Optional.of(device));
    when(devices.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.recordReading("node-1", "temperature", "21.5");

    Sensor sensor = device.getSensors().getFirst();
    assertEquals("21.5", sensor.getValue());
    assertEquals(NOW, sensor.getUpdatedAt());
    verify(devices).save(device);
  }

  @DisplayName("recordReading() auto-provisions an unknown device as a sensor node")
  @Test
  void recordReading_autoProvisionsUnknownDeviceAsSensorNode() {
    when(devices.findByExternalId("living-room")).thenReturn(Optional.empty());
    when(devices.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.recordReading("living-room", "temperature", "21.5");

    ArgumentCaptor<Device> saved = ArgumentCaptor.forClass(Device.class);
    verify(devices).save(saved.capture());
    Device device = saved.getValue();
    assertEquals("living-room", device.getExternalId());
    assertEquals(DeviceType.SENSOR_NODE, device.getType());
    assertEquals(1, device.getSensors().size());
    Sensor sensor = device.getSensors().getFirst();
    assertEquals("temperature", sensor.getKey());
    assertEquals(SensorType.TEMPERATURE, sensor.getType());
    assertEquals("°C", sensor.getUnit());
    assertEquals("21.5", sensor.getValue());
    assertEquals(NOW, sensor.getUpdatedAt());
  }

  @DisplayName("recordReading() auto-adds a recognized sensor the device had not declared")
  @Test
  void recordReading_autoAddsRecognizedSensorToExistingDevice() {
    Device device = new Device("node-1", "Climate", DeviceType.SENSOR_NODE, null);
    device.addSensor("temperature", SensorType.TEMPERATURE, "°C");
    when(devices.findByExternalId("node-1")).thenReturn(Optional.of(device));
    when(devices.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.recordReading("node-1", "humidity", "40");

    Optional<Sensor> humidity =
        device.getSensors().stream()
            .filter(sensor -> sensor.getKey().equals("humidity"))
            .findFirst();
    assertTrue(humidity.isPresent());
    assertEquals(SensorType.HUMIDITY, humidity.get().getType());
    assertEquals("%", humidity.get().getUnit());
    assertEquals("40", humidity.get().getValue());
    verify(devices).save(device);
  }

  @DisplayName("recordReading() drops a reading whose sensor key is not a known measurement")
  @Test
  void recordReading_dropsReadingForUnrecognizedSensorKey() {
    service.recordReading("node-1", "noise", "42");

    verify(devices, never()).save(any());
    verify(events, never()).publishEvent(any());
  }

  @DisplayName("recordReading() publishes a telemetry event for the recorded reading")
  @Test
  void recordReading_publishesTelemetryEvent() {
    Device device = new Device("node-1", "Climate", DeviceType.SENSOR_NODE, null);
    device.addSensor("temperature", SensorType.TEMPERATURE, "°C");
    when(devices.findByExternalId("node-1")).thenReturn(Optional.of(device));
    when(devices.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.recordReading("node-1", "temperature", "21.5");

    SensorReadingRecorded event = publishedEventOfType(SensorReadingRecorded.class);
    assertEquals("node-1", event.deviceExternalId());
    assertEquals("temperature", event.sensorKey());
    assertEquals(SensorType.TEMPERATURE, event.type());
    assertEquals("°C", event.unit());
    assertEquals("21.5", event.value());
    assertEquals(NOW, event.at());
  }

  @DisplayName("recordReading() pushes the device's new latest reading to live clients")
  @Test
  void recordReading_publishesDeviceChangedSnapshot() {
    Device device = new Device("node-1", "Climate", DeviceType.SENSOR_NODE, null);
    device.addSensor("temperature", SensorType.TEMPERATURE, "°C");
    when(devices.findByExternalId("node-1")).thenReturn(Optional.of(device));
    when(devices.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.recordReading("node-1", "temperature", "21.5");

    DeviceChanged event = publishedEventOfType(DeviceChanged.class);
    assertEquals("node-1", event.device().externalId());
    assertEquals("21.5", event.device().sensors().getFirst().value());
  }

  @DisplayName("toggle() pushes the device's new state to live clients")
  @Test
  void toggle_publishesDeviceChanged() {
    Device device = new Device("ext-1", "Plug", DeviceType.SHELLY_PLUG, "shelly");
    when(devices.findById(1L)).thenReturn(Optional.of(device));
    when(adapters.get("shelly")).thenReturn(adapter);
    when(devices.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.toggle(1L);

    DeviceChanged event = publishedEventOfType(DeviceChanged.class);
    assertEquals("ext-1", event.device().externalId());
    assertEquals("true", event.device().state().get("on"));
  }

  @DisplayName("register() pushes the new device to live clients")
  @Test
  void register_publishesDeviceChanged() {
    when(adapters.supports("shelly")).thenReturn(true);
    when(devices.findByExternalId("ext-1")).thenReturn(Optional.empty());
    when(devices.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.register("ext-1", "Plug", DeviceType.SHELLY_PLUG, "shelly", Set.of(), List.of());

    DeviceChanged event = publishedEventOfType(DeviceChanged.class);
    assertEquals("ext-1", event.device().externalId());
  }

  @DisplayName("applyCommand() pushes the device's new state to live clients")
  @Test
  void applyCommand_publishesDeviceChanged() {
    Device device = richLight();
    when(devices.findById(1L)).thenReturn(Optional.of(device));
    when(adapters.get("hue")).thenReturn(adapter);
    when(devices.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.applyCommand(1L, new CommandRequest(null, 60, null, null));

    DeviceChanged event = publishedEventOfType(DeviceChanged.class);
    assertEquals("light-1", event.device().externalId());
    assertEquals("60", event.device().state().get("brightness"));
  }

  @DisplayName("delete() tells live clients the device is gone")
  @Test
  void delete_publishesDeviceRemoved() {
    when(devices.existsById(1L)).thenReturn(true);

    service.delete(1L);

    verify(events).publishEvent(new DeviceRemoved(1L));
  }

  @DisplayName("assignRoom() sets the device's room and pushes it to live clients")
  @Test
  void assignRoom_setsRoomAndPublishes() {
    Device device = new Device("ext-1", "Plug", DeviceType.SHELLY_PLUG, "shelly");
    Room room = new Room("Kitchen");
    when(devices.findById(1L)).thenReturn(Optional.of(device));
    when(rooms.findById(9L)).thenReturn(Optional.of(room));
    when(devices.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Device result = service.assignRoom(1L, 9L);

    assertSame(room, result.getRoom());
    DeviceChanged event = publishedEventOfType(DeviceChanged.class);
    assertEquals("Kitchen", event.device().roomName());
  }

  @DisplayName("assignRoom() rejects an unknown room")
  @Test
  void assignRoom_rejectsUnknownRoom() {
    Device device = new Device("ext-1", "Plug", DeviceType.SHELLY_PLUG, "shelly");
    when(devices.findById(1L)).thenReturn(Optional.of(device));
    when(rooms.findById(9L)).thenReturn(Optional.empty());

    assertThrows(RoomNotFoundException.class, () -> service.assignRoom(1L, 9L));
    verify(devices, never()).save(any());
  }

  @DisplayName("assignRoom() rejects an unknown device")
  @Test
  void assignRoom_rejectsUnknownDevice() {
    when(devices.findById(1L)).thenReturn(Optional.empty());

    assertThrows(DeviceNotFoundException.class, () -> service.assignRoom(1L, 9L));
  }

  @DisplayName("clearRoom() unassigns the device and pushes it to live clients")
  @Test
  void clearRoom_unassignsAndPublishes() {
    Device device = new Device("ext-1", "Plug", DeviceType.SHELLY_PLUG, "shelly");
    device.assignRoom(new Room("Kitchen"));
    when(devices.findById(1L)).thenReturn(Optional.of(device));
    when(devices.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Device result = service.clearRoom(1L);

    assertNull(result.getRoom());
    DeviceChanged event = publishedEventOfType(DeviceChanged.class);
    assertNull(event.device().roomId());
  }

  @DisplayName("a removed room unassigns each of its devices and pushes them to live clients")
  @Test
  void onRoomRemoved_unassignsEachDevice() {
    Device a = new Device("ext-a", "A", DeviceType.SHELLY_PLUG, "shelly");
    Device b = new Device("ext-b", "B", DeviceType.SHELLY_PLUG, "shelly");
    Room room = new Room("Kitchen");
    a.assignRoom(room);
    b.assignRoom(room);
    when(devices.findByRoomId(9L)).thenReturn(List.of(a, b));
    when(devices.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.onRoomRemoved(new RoomRemoved(9L));

    assertNull(a.getRoom());
    assertNull(b.getRoom());
    verify(devices).save(a);
    verify(devices).save(b);
    verify(events, times(2)).publishEvent(any(DeviceChanged.class));
  }

  private <T> T publishedEventOfType(Class<T> type) {
    ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
    verify(events, atLeastOnce()).publishEvent(published.capture());
    return published.getAllValues().stream()
        .filter(type::isInstance)
        .map(type::cast)
        .findFirst()
        .orElseThrow(
            () -> new AssertionError("no published event of type " + type.getSimpleName()));
  }

  @DisplayName("applyCommand() sets brightness and turns an off device on")
  @Test
  void applyCommand_setsBrightnessAndTurnsAnOffDeviceOn() {
    Device device = richLight();
    when(devices.findById(1L)).thenReturn(Optional.of(device));
    when(adapters.get("hue")).thenReturn(adapter);
    when(devices.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Device result = service.applyCommand(1L, new CommandRequest(null, 60, null, null));

    assertEquals("60", result.getState().get("brightness"));
    assertEquals("true", result.getState().get("on"));
    verify(adapter).sendCommand(eq("light-1"), commandCaptor.capture());
    Map<String, Object> payload = commandCaptor.getValue();
    assertEquals(60, payload.get("brightness"));
    assertEquals(true, payload.get("on"));
  }

  @DisplayName("applyCommand() sets the color and records the XY color mode")
  @Test
  void applyCommand_setsColorAndRecordsXyColorMode() {
    Device device = richLight();
    when(devices.findById(1L)).thenReturn(Optional.of(device));
    when(adapters.get("hue")).thenReturn(adapter);
    when(devices.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Device result =
        service.applyCommand(1L, new CommandRequest(null, null, new XyColor(0.3, 0.3), null));

    assertEquals("0.3,0.3", result.getState().get("colorXy"));
    assertEquals("XY", result.getState().get("colorMode"));
  }

  @DisplayName("applyCommand() respects an explicit off request")
  @Test
  void applyCommand_respectsAnExplicitOff() {
    Device device = richLight();
    when(devices.findById(1L)).thenReturn(Optional.of(device));
    when(adapters.get("hue")).thenReturn(adapter);
    when(devices.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Device result = service.applyCommand(1L, new CommandRequest(false, null, null, null));

    verify(adapter).sendCommand(eq("light-1"), commandCaptor.capture());
    assertEquals(false, commandCaptor.getValue().get("on"));
    assertEquals("false", result.getState().get("on"));
  }

  @DisplayName("applyCommand() rejects color and color temperature in one command")
  @Test
  void applyCommand_rejectsColorAndColorTemperatureTogether() {
    Device device = richLight();
    when(devices.findById(1L)).thenReturn(Optional.of(device));

    assertThrows(
        InvalidCommandException.class,
        () ->
            service.applyCommand(1L, new CommandRequest(null, null, new XyColor(0.3, 0.3), 3000)));

    verify(devices, never()).save(any());
  }

  @DisplayName("applyCommand() rejects an attribute for a capability the device lacks")
  @Test
  void applyCommand_rejectsAttributeForCapabilityTheDeviceLacks() {
    Device plug = new Device("ext-1", "Plug", DeviceType.SHELLY_PLUG, "shelly");
    when(devices.findById(1L)).thenReturn(Optional.of(plug));

    assertThrows(
        UnsupportedCapabilityException.class,
        () -> service.applyCommand(1L, new CommandRequest(null, 50, null, null)));

    verify(devices, never()).save(any());
  }

  @DisplayName("applyCommand() rejects a value outside the contract range")
  @Test
  void applyCommand_rejectsValueOutsideTheContractRange() {
    Device device = richLight();
    when(devices.findById(1L)).thenReturn(Optional.of(device));

    assertThrows(
        InvalidCommandException.class,
        () -> service.applyCommand(1L, new CommandRequest(null, 0, null, null)));

    verify(devices, never()).save(any());
  }

  @DisplayName("applyCommand() rejects an empty command")
  @Test
  void applyCommand_rejectsAnEmptyCommand() {
    Device device = richLight();
    when(devices.findById(1L)).thenReturn(Optional.of(device));

    assertThrows(
        InvalidCommandException.class,
        () -> service.applyCommand(1L, new CommandRequest(null, null, null, null)));

    verify(devices, never()).save(any());
  }

  @DisplayName("delete() removes an existing device")
  @Test
  void delete_removesExistingDevice() {
    when(devices.existsById(1L)).thenReturn(true);

    service.delete(1L);

    verify(devices).deleteById(1L);
  }

  @DisplayName("delete() throws when the device does not exist")
  @Test
  void delete_throwsWhenDeviceMissing() {
    when(devices.existsById(99L)).thenReturn(false);

    assertThrows(DeviceNotFoundException.class, () -> service.delete(99L));

    verify(devices, never()).deleteById(any());
  }

  private static Device richLight() {
    return new Device(
        "light-1",
        "Lamp",
        DeviceType.HUE_LIGHT,
        "hue",
        Set.of(
            Capability.SWITCHABLE,
            Capability.DIMMABLE,
            Capability.COLOR,
            Capability.COLOR_TEMPERATURE));
  }
}
