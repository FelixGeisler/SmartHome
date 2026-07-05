package org.felixgeisler.smarthome.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.util.Set;
import java.util.function.Consumer;
import org.felixgeisler.smarthome.device.Device;
import org.felixgeisler.smarthome.device.DeviceChanged;
import org.felixgeisler.smarthome.device.DeviceRemoved;
import org.felixgeisler.smarthome.device.DeviceResponse;
import org.felixgeisler.smarthome.device.DeviceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class DeviceEventBroadcasterTest {

  // A direct executor keeps the broadcast synchronous in tests; production uses its own thread.
  private final DeviceEventBroadcaster broadcaster =
      new DeviceEventBroadcaster(JsonMapper.builder().build(), Runnable::run);

  @DisplayName("a device-changed event is sent to a registered client")
  @Test
  void onDeviceChanged_sendsToRegisteredClient() throws IOException {
    SseEmitter emitter = mock(SseEmitter.class);
    broadcaster.add(emitter);

    broadcaster.onDeviceChanged(new DeviceChanged(sampleDevice()));

    verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
  }

  @DisplayName("a device-removed event is sent to a registered client")
  @Test
  void onDeviceRemoved_sendsToRegisteredClient() throws IOException {
    SseEmitter emitter = mock(SseEmitter.class);
    broadcaster.add(emitter);

    broadcaster.onDeviceRemoved(new DeviceRemoved(7L));

    verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
  }

  @DisplayName("a client whose send fails is dropped from later broadcasts")
  @Test
  void failedClient_isDroppedFromLaterBroadcasts() throws IOException {
    SseEmitter emitter = mock(SseEmitter.class);
    doThrow(new IOException("client gone"))
        .when(emitter)
        .send(any(SseEmitter.SseEventBuilder.class));
    broadcaster.add(emitter);

    broadcaster.onDeviceChanged(new DeviceChanged(sampleDevice()));
    broadcaster.onDeviceChanged(new DeviceChanged(sampleDevice()));

    // The first send throws and drops the emitter, so the second broadcast never reaches it.
    verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
  }

  @DisplayName("a client whose stream completed stops receiving broadcasts")
  @Test
  void completedClient_stopsReceivingBroadcasts() throws IOException {
    SseEmitter emitter = mock(SseEmitter.class);
    ArgumentCaptor<Runnable> onCompletion = ArgumentCaptor.forClass(Runnable.class);
    broadcaster.add(emitter);
    verify(emitter).onCompletion(onCompletion.capture());

    onCompletion.getValue().run();
    broadcaster.onDeviceChanged(new DeviceChanged(sampleDevice()));

    verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
  }

  @DisplayName("a client whose stream timed out stops receiving broadcasts")
  @Test
  void timedOutClient_stopsReceivingBroadcasts() throws IOException {
    SseEmitter emitter = mock(SseEmitter.class);
    ArgumentCaptor<Runnable> onTimeout = ArgumentCaptor.forClass(Runnable.class);
    broadcaster.add(emitter);
    verify(emitter).onTimeout(onTimeout.capture());

    onTimeout.getValue().run();
    broadcaster.onDeviceChanged(new DeviceChanged(sampleDevice()));

    verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
  }

  @DisplayName("a client whose stream errored stops receiving broadcasts")
  @Test
  void erroredClient_stopsReceivingBroadcasts() throws IOException {
    SseEmitter emitter = mock(SseEmitter.class);
    // ArgumentCaptor.forClass cannot express Consumer<Throwable>; the cast is safe because
    // SseEmitter.onError only ever accepts that exact type.
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Consumer<Throwable>> onError = ArgumentCaptor.forClass(Consumer.class);
    broadcaster.add(emitter);
    verify(emitter).onError(onError.capture());

    onError.getValue().accept(new IOException("connection reset"));
    broadcaster.onDeviceChanged(new DeviceChanged(sampleDevice()));

    verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
  }

  @DisplayName("shutdown completes every open stream so it cannot stall the container")
  @Test
  void contextClose_completesEveryOpenStream() {
    SseEmitter emitter = mock(SseEmitter.class);
    broadcaster.add(emitter);

    broadcaster.completeAll(new ContextClosedEvent(mock(ApplicationContext.class)));

    verify(emitter).complete();
  }

  @DisplayName("an unserializable payload is dropped, and the failure is logged not propagated")
  @Test
  void unserializablePayload_isDroppedAndLogged() throws IOException {
    ObjectMapper failing = mock(ObjectMapper.class);
    when(failing.writeValueAsString(any())).thenThrow(new JacksonException("boom") {});
    DeviceEventBroadcaster subject = new DeviceEventBroadcaster(failing, Runnable::run);
    SseEmitter emitter = mock(SseEmitter.class);
    subject.add(emitter);
    Logger logger = (Logger) LoggerFactory.getLogger(DeviceEventBroadcaster.class);
    ListAppender<ILoggingEvent> logged = new ListAppender<>();
    logged.start();
    logger.addAppender(logged);
    // Capture the expected error here and keep it off the console, so the drop is asserted rather
    // than left as an alarming stack trace in the build output.
    logger.setAdditive(false);

    try {
      subject.onDeviceChanged(new DeviceChanged(sampleDevice()));
    } finally {
      logger.setAdditive(true);
      logger.detachAppender(logged);
    }

    // The bad event reaches no client: the serialization failure is swallowed, not propagated.
    verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    verify(emitter, never()).send(anyString());
    // And it is recorded, so a real serialization bug would surface in the logs rather than vanish.
    assertEquals(1, logged.list.size(), "the drop should be logged exactly once");
    ILoggingEvent event = logged.list.get(0);
    assertEquals(Level.ERROR, event.getLevel());
    assertTrue(
        event.getFormattedMessage().contains("Cannot serialize a live-update payload"),
        "the log should explain that the payload could not be serialized");
    assertNotNull(
        event.getThrowableProxy(), "the serialization failure should be attached to the log");
    assertEquals("boom", event.getThrowableProxy().getMessage());
  }

  private static DeviceResponse sampleDevice() {
    return DeviceResponse.from(
        new Device("ext-1", "Plug", DeviceType.SHELLY_PLUG, "shelly", Set.of()));
  }
}
