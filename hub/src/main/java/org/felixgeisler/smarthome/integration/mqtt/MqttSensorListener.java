package org.felixgeisler.smarthome.integration.mqtt;

import java.nio.charset.StandardCharsets;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.felixgeisler.smarthome.device.DeviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Routes inbound MQTT messages to the device service as sensor readings.
 *
 * <p>Topics follow {@code <prefix>/<device-externalId>/<sensor-key>} and the payload is the
 * reading value; the device's external id and the sensor key are the last two segments. Topics
 * that do not carry both are logged and dropped.
 */
@Component
public class MqttSensorListener implements MqttCallback {

  private static final Logger log = LoggerFactory.getLogger(MqttSensorListener.class);

  // A reading topic ends with the device's external id and then the sensor key.
  private static final int MIN_SEGMENTS = 2;

  private final DeviceService devices;

  /**
   * Creates the listener.
   *
   * @param devices the device service that persists readings
   */
  public MqttSensorListener(DeviceService devices) {
    this.devices = devices;
  }

  @Override
  public void messageArrived(String topic, MqttMessage message) {
    String[] segments = topic.split("/");
    if (segments.length < MIN_SEGMENTS) {
      log.warn("Dropping reading on unparseable topic '{}'", topic);
      return;
    }
    String externalId = segments[segments.length - 2];
    String sensorKey = segments[segments.length - 1];
    String value = new String(message.getPayload(), StandardCharsets.UTF_8).trim();
    devices.recordReading(externalId, sensorKey, value);
  }

  @Override
  public void connectionLost(Throwable cause) {
    String reason = cause == null ? "unknown cause" : cause.getMessage();
    log.warn("MQTT connection lost: {}", reason);
  }

  @Override
  public void deliveryComplete(IMqttDeliveryToken token) {
    // The hub only subscribes; it never publishes, so delivery callbacks carry no work.
  }
}
