package org.felixgeisler.smarthome.integration.mqtt;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.felixgeisler.smarthome.device.DeviceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MqttSensorListenerTest {

  @Mock private DeviceService devices;

  private MqttSensorListener listener;

  @BeforeEach
  void setUp() {
    listener = new MqttSensorListener(devices);
  }

  @DisplayName("messageArrived() records a reading parsed from the topic and payload")
  @Test
  void messageArrived_recordsReadingFromTopicAndPayload() {
    listener.messageArrived("home/node-1/temperature", new MqttMessage("21.5".getBytes(UTF_8)));

    verify(devices).recordReading("node-1", "temperature", "21.5");
  }

  @DisplayName("messageArrived() trims surrounding whitespace from the payload")
  @Test
  void messageArrived_trimsSurroundingWhitespaceFromPayload() {
    listener.messageArrived("home/node-1/humidity", new MqttMessage(" 40 \n".getBytes(UTF_8)));

    verify(devices).recordReading("node-1", "humidity", "40");
  }

  @DisplayName("messageArrived() drops a topic missing the device and sensor segments")
  @Test
  void messageArrived_dropsTopicWithoutDeviceAndSensorSegments() {
    listener.messageArrived("home", new MqttMessage("21.5".getBytes(UTF_8)));

    verifyNoInteractions(devices);
  }
}
