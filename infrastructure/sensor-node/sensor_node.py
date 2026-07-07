#!/usr/bin/env python3
"""Publish BME690 (and optionally SCD4x CO2) readings to the SmartHome MQTT broker.

Reads a BME690 over I2C for temperature, humidity, and pressure, and, when enabled, an
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
from collections import deque
from pathlib import Path

import bme690
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
# The BME690 gas element must warm up before its resistance settles; no air-quality score is
# published within this many seconds of startup.
GAS_BURN_IN_SECONDS = float(os.environ.get("GAS_BURN_IN_SECONDS", "300"))
# The air-quality baseline self-calibrates to the cleanest air seen over this rolling window, so
# the score adapts to the room and to the sensor's slow drift as it ages.
AIR_QUALITY_WINDOW_SECONDS = float(os.environ.get("AIR_QUALITY_WINDOW_SECONDS", str(24 * 3600)))

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


# Optimal indoor relative humidity (%), and humidity's share of the air-quality score; the gas
# resistance carries the rest.
_HUMIDITY_BASELINE = 40.0
_HUMIDITY_WEIGHT = 25.0
# How many time buckets the rolling baseline keeps, capping its memory whatever the sample rate.
_BASELINE_BUCKETS = 288


class AirQuality:
    """Turns BME690 gas resistance and humidity into a 0-100 air-quality score (higher is better).

    The gas element has no absolute calibration, so the score is relative to a self-calibrating
    baseline: the cleanest air (highest gas resistance) seen over a rolling window, default 24h.
    The first ``burn_in_seconds`` only let the heated element settle and no score is published
    until then; after that the baseline keeps adapting as the window slides, so it tracks the room
    and the sensor's slow drift as it ages. Expect the first day or two to read rough while the
    window fills with the range of the room's air.

    The window is downsampled to at most a few hundred time buckets (keeping each bucket's peak
    resistance), so memory stays bounded no matter how often the sensor is sampled.
    """

    def __init__(self, burn_in_seconds, window_seconds, clock=time.monotonic):
        self._burn_in_seconds = burn_in_seconds
        self._window_seconds = window_seconds
        self._bucket_seconds = max(window_seconds / _BASELINE_BUCKETS, 1.0)
        self._clock = clock
        self._start = clock()
        self._buckets = deque()  # (bucket start time, peak resistance seen in the bucket)

    def score(self, gas_resistance, humidity):
        """Return the 0-100 score, or None until the gas element has burned in."""
        now = self._clock()
        self._observe(now, gas_resistance)
        if now - self._start < self._burn_in_seconds:
            return None
        baseline = max(resistance for _, resistance in self._buckets)
        return round(self._humidity_score(humidity) + self._gas_score(gas_resistance, baseline))

    def _observe(self, now, gas_resistance):
        # Drop anything older than the window so only recently seen clean air sets the baseline.
        cutoff = now - self._window_seconds
        while self._buckets and self._buckets[0][0] < cutoff:
            self._buckets.popleft()
        # Downsample to one bucket per interval, keeping the peak (cleanest) resistance in it.
        if self._buckets and now - self._buckets[-1][0] < self._bucket_seconds:
            start, peak = self._buckets[-1]
            self._buckets[-1] = (start, max(peak, gas_resistance))
        else:
            self._buckets.append((now, gas_resistance))

    @staticmethod
    def _humidity_score(humidity):
        offset = humidity - _HUMIDITY_BASELINE
        if offset > 0:
            span = 100.0 - _HUMIDITY_BASELINE
            return (span - offset) / span * _HUMIDITY_WEIGHT
        return (_HUMIDITY_BASELINE + offset) / _HUMIDITY_BASELINE * _HUMIDITY_WEIGHT

    @staticmethod
    def _gas_score(gas_resistance, baseline):
        gas_weight = 100.0 - _HUMIDITY_WEIGHT
        # The current reading is inside the window, so it never exceeds the baseline; guard a
        # degenerate zero baseline so a freak sample cannot crash the publish loop.
        if baseline <= 0:
            return gas_weight
        return min(gas_resistance / baseline, 1.0) * gas_weight


def open_environment_sensor():
    """Initialise the BME690 with its own Bosch driver, auto-detecting its I2C address.

    Honours an explicit I2C_ADDRESS; otherwise probes the two common addresses (0x77, 0x76)
    and uses the first that responds. Raises if none do.
    """
    candidates = (BME690_ADDRESS,) if BME690_ADDRESS is not None else BME690_CANDIDATES
    last_error = None
    for address in candidates:
        try:
            # The BME690 needs the BME690 driver, not the BME680 one: the two share the 0x61 chip
            # id, but the BME690 changed the pressure compensation, so the BME680 driver reports
            # pressure roughly three times too high.
            sensor = bme690.BME690(address)
            sensor.set_temperature_oversample(bme690.OS_8X)
            sensor.set_humidity_oversample(bme690.OS_2X)
            sensor.set_pressure_oversample(bme690.OS_4X)
            sensor.set_filter(bme690.FILTER_SIZE_3)
            # Enable the gas (VOC) element that drives the air-quality score.
            sensor.set_gas_heater_temperature(320)
            sensor.set_gas_heater_duration(150)
            sensor.select_gas_heater_profile(0)
            sensor.set_gas_status(bme690.ENABLE_GAS_MEAS)
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


def read_environment(sensor, air_quality):
    """Sample the BME690: temperature (°C), humidity (%), pressure (hPa), and, once the gas element
    has warmed up, an air-quality score (0-100, higher is better).

    Returns nothing when no fresh sample is ready this cycle, so a not-yet-ready reading is
    skipped rather than published with a stale value.
    """
    if not sensor.get_sensor_data():
        return {}
    data = sensor.data
    readings = {
        "temperature": f"{data.temperature:.1f}",
        "humidity": f"{data.humidity:.1f}",
        "pressure": f"{data.pressure:.1f}",
    }
    # Only score once the heater has reached temperature, so an unstable reading is not counted.
    if data.heat_stable:
        score = air_quality.score(data.gas_resistance, data.humidity)
        if score is not None:
            readings["airQuality"] = str(score)
    return readings


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
    air_quality = AirQuality(GAS_BURN_IN_SECONDS, AIR_QUALITY_WINDOW_SECONDS)
    client = build_client()
    log.info(
        "Publishing as node '%s' to %s:%s every %ss",
        NODE_ID, BROKER_HOST, BROKER_PORT, INTERVAL_SECONDS,
    )

    try:
        while _running:
            readings = {}
            try:
                readings.update(read_environment(env_sensor, air_quality))
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
