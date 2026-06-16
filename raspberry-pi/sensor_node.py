#!/usr/bin/env python3
"""Publish BME280 (and optionally SCD4x CO2) readings to the SmartHome MQTT broker.

Reads a BME280 over I2C for temperature, humidity, and pressure, and — when enabled — an
SCD4x (SCD40/SCD41) for CO2, publishing each to ``<prefix>/<node-id>/<sensor-key>`` (e.g.
``home/living-room/temperature``), which is the topic shape the SmartHome hub subscribes to.
Values are plain strings; the unit for each reading is declared on the hub when the device is
registered, not here.

Configuration comes from environment variables (see ``sensor-node.env.example``); no secrets
are hard-coded.
"""
from __future__ import annotations

import logging
import os
import signal
import time

import bme280
import paho.mqtt.client as mqtt
import smbus2

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("sensor-node")

BROKER_HOST = os.environ.get("MQTT_BROKER_HOST", "localhost")
BROKER_PORT = int(os.environ.get("MQTT_BROKER_PORT", "1883"))
USERNAME = os.environ.get("MQTT_USERNAME")
PASSWORD = os.environ.get("MQTT_PASSWORD")
NODE_ID = os.environ.get("NODE_ID", "living-room")
TOPIC_PREFIX = os.environ.get("TOPIC_PREFIX", "home")
INTERVAL_SECONDS = float(os.environ.get("INTERVAL_SECONDS", "30"))
I2C_BUS = int(os.environ.get("I2C_BUS", "1"))
I2C_ADDRESS = int(os.environ.get("I2C_ADDRESS", "0x76"), 16)
ENABLE_CO2 = os.environ.get("ENABLE_CO2", "true").lower() == "true"

_running = True


def _stop(signum, _frame):
    """Signal handler: ask the main loop to exit so we shut down cleanly."""
    global _running
    log.info("Received signal %s; shutting down", signum)
    _running = False


def _interruptible_sleep(seconds):
    """Sleep in 1s steps so a shutdown signal is handled promptly."""
    slept = 0.0
    while _running and slept < seconds:
        time.sleep(min(1.0, seconds - slept))
        slept += 1.0


def open_co2_sensor():
    """Start the SCD4x periodic measurement, or return None if disabled or absent.

    The imports are local so a BME280-only node (ENABLE_CO2=false) needs neither the SCD4x
    library nor Blinka installed.
    """
    if not ENABLE_CO2:
        return None
    try:
        import adafruit_scd4x
        import board

        sensor = adafruit_scd4x.SCD4X(board.I2C())
        sensor.start_periodic_measurement()
        log.info("SCD4x CO2 sensor initialised (0x62)")
        return sensor
    except Exception as exc:  # any init failure (missing lib or sensor): degrade gracefully
        log.warning("CO2 sensor unavailable (%s); publishing without CO2", exc)
        return None


def read_environment(bus, calibration):
    """Sample the BME280: temperature (°C), humidity (%), pressure (hPa)."""
    sample = bme280.sample(bus, I2C_ADDRESS, calibration)
    return {
        "temperature": f"{sample.temperature:.1f}",
        "humidity": f"{sample.humidity:.1f}",
        "pressure": f"{sample.pressure:.1f}",
    }


def read_co2(sensor):
    """Read CO2 (ppm) from the SCD4x, or nothing if it is absent or has no fresh sample yet."""
    if sensor is None or not sensor.data_ready:
        return {}
    return {"co2": str(sensor.CO2)}


def build_client():
    """Create and connect the MQTT client, retrying until the broker is reachable."""
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=f"sensor-{NODE_ID}")
    if USERNAME:
        client.username_pw_set(USERNAME, PASSWORD)
    client.reconnect_delay_set(min_delay=1, max_delay=30)
    while _running:
        try:
            client.connect(BROKER_HOST, BROKER_PORT, keepalive=60)
            break
        except OSError as exc:
            log.warning("Broker %s:%s unavailable (%s); retrying in 5s", BROKER_HOST, BROKER_PORT, exc)
            time.sleep(5)
    client.loop_start()
    return client


def publish(client, readings):
    """Publish each reading, retained so the hub gets the last value on (re)subscribe."""
    for key, value in readings.items():
        topic = f"{TOPIC_PREFIX}/{NODE_ID}/{key}"
        client.publish(topic, value, qos=1, retain=True)
        log.info("Published %s = %s", topic, value)


def main():
    signal.signal(signal.SIGINT, _stop)
    signal.signal(signal.SIGTERM, _stop)

    bus = smbus2.SMBus(I2C_BUS)
    calibration = bme280.load_calibration_params(bus, I2C_ADDRESS)
    co2_sensor = open_co2_sensor()
    client = build_client()
    log.info(
        "Publishing as node '%s' to %s:%s every %ss",
        NODE_ID, BROKER_HOST, BROKER_PORT, INTERVAL_SECONDS,
    )

    try:
        while _running:
            readings = {}
            try:
                readings.update(read_environment(bus, calibration))
            except OSError as exc:  # transient I2C read error: log and keep going
                log.warning("BME280 read failed: %s", exc)
            try:
                readings.update(read_co2(co2_sensor))
            except OSError as exc:
                log.warning("SCD4x read failed: %s", exc)
            publish(client, readings)
            _interruptible_sleep(INTERVAL_SECONDS)
    finally:
        client.loop_stop()
        client.disconnect()
        bus.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
