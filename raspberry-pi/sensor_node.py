#!/usr/bin/env python3
"""Publish BME690 (and optionally SCD4x CO2) readings to the SmartHome MQTT broker.

Reads a BME690 over I2C for temperature, humidity, and pressure, and — when enabled — an
SCD4x (SCD40/SCD41) for CO2, publishing each to ``<prefix>/<node-id>/<sensor-key>`` (e.g.
``home/living-room/temperature``), the topic shape the SmartHome hub subscribes to. Values are
plain strings; the unit for each reading is declared on the hub when the device is registered,
not here.

Configuration comes from environment variables (see ``sensor-node.env.example``); no secrets
are hard-coded.
"""
from __future__ import annotations

import logging
import os
import signal
import time
from pathlib import Path

import adafruit_bme680  # also drives the BME690 (shared chip ID 0x61)
import board
import paho.mqtt.client as mqtt

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("sensor-node")


def _load_env_file(path):
    """Populate the environment from a KEY=VALUE file; existing variables take precedence.

    Lets ``python sensor_node.py`` pick up ``sensor-node.env`` without a wrapper. The systemd
    unit still loads the same file via EnvironmentFile, which sets the variables first, so those
    win and this call becomes a no-op there.
    """
    if not path.exists():
        return
    for line in path.read_text().splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        key, sep, value = stripped.partition("=")
        if sep and key.strip() not in os.environ:
            os.environ[key.strip()] = value.strip()


# Load config from sensor-node.env next to this script, regardless of the working directory.
_load_env_file(Path(__file__).with_name("sensor-node.env"))

BROKER_HOST = os.environ.get("MQTT_BROKER_HOST", "localhost")
BROKER_PORT = int(os.environ.get("MQTT_BROKER_PORT", "1883"))
USERNAME = os.environ.get("MQTT_USERNAME")
PASSWORD = os.environ.get("MQTT_PASSWORD")
NODE_ID = os.environ.get("NODE_ID", "living-room")
TOPIC_PREFIX = os.environ.get("TOPIC_PREFIX", "home")
INTERVAL_SECONDS = float(os.environ.get("INTERVAL_SECONDS", "5"))
# Optional override; when unset, the BME690 address is auto-detected.
_configured_address = os.environ.get("I2C_ADDRESS")
BME690_ADDRESS = int(_configured_address, 16) if _configured_address else None
# The two addresses BME690 boards use, in probe order.
BME690_CANDIDATES = (0x77, 0x76)
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


def open_environment_sensor():
    """Initialise the BME690, auto-detecting its I2C address.

    Honours an explicit I2C_ADDRESS; otherwise probes the two common addresses (0x77, 0x76)
    and uses the first that responds. Raises if none do.
    """
    i2c = board.I2C()
    candidates = (BME690_ADDRESS,) if BME690_ADDRESS is not None else BME690_CANDIDATES
    last_error = None
    for address in candidates:
        try:
            # The BME690 shares the BME680/BME688 chip ID (0x61), so the Adafruit BME680 driver
            # reads it.
            sensor = adafruit_bme680.Adafruit_BME680_I2C(i2c, address=address)
            log.info("BME690 found at 0x%02x", address)
            return sensor
        except (ValueError, OSError, RuntimeError) as exc:  # nothing here: try the next address
            last_error = exc
            log.info("No BME690 at 0x%02x (%s)", address, exc)
    addresses = ", ".join(f"0x{a:02x}" for a in candidates)
    raise RuntimeError(
        f"No BME690 found at {addresses}; check wiring and `i2cdetect -y 1`"
    ) from last_error


def open_co2_sensor():
    """Start the SCD4x periodic measurement, or return None if disabled or absent.

    The import is local so a node without a CO2 sensor (ENABLE_CO2=false) needs neither the
    SCD4x library installed nor the sensor present.
    """
    if not ENABLE_CO2:
        return None
    try:
        import adafruit_scd4x

        sensor = adafruit_scd4x.SCD4X(board.I2C())
        sensor.start_periodic_measurement()
        log.info("SCD4x CO2 sensor initialised (0x62)")
        return sensor
    except Exception as exc:  # any init failure (missing lib or sensor): degrade gracefully
        log.warning("CO2 sensor unavailable (%s); publishing without CO2", exc)
        return None


def read_environment(sensor):
    """Sample the BME690: temperature (°C), humidity (%), pressure (hPa)."""
    return {
        "temperature": f"{sensor.temperature:.1f}",
        "humidity": f"{sensor.humidity:.1f}",
        "pressure": f"{sensor.pressure:.1f}",
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

    env_sensor = open_environment_sensor()
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
                readings.update(read_environment(env_sensor))
            except (OSError, RuntimeError) as exc:  # transient I2C read error: log and keep going
                log.warning("BME690 read failed: %s", exc)
            try:
                readings.update(read_co2(co2_sensor))
            except (OSError, RuntimeError) as exc:
                log.warning("SCD4x read failed: %s", exc)
            publish(client, readings)
            _interruptible_sleep(INTERVAL_SECONDS)
    finally:
        client.loop_stop()
        client.disconnect()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
