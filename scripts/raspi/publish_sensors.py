#!/usr/bin/env python3
"""Read SCD41 + BME690 and publish each metric to MQTT.

Topic structure: smarthome/sensors/<DEVICE_ID>/<metric>
DEVICE_ID defaults to the Pi's hostname so each Pi auto-identifies. Override
with the DEVICE_ID env var when running multiple Pis on the same broker.
"""

import os
import socket
import time
import board
import busio
import adafruit_scd4x
import bme690
import paho.mqtt.client as mqtt

DEVICE_ID = os.environ.get("DEVICE_ID", socket.gethostname())
BROKER    = os.environ.get("MQTT_BROKER", "localhost")
PREFIX    = os.environ.get("MQTT_TOPIC_PREFIX", "smarthome/sensors")
INTERVAL  = int(os.environ.get("SAMPLE_INTERVAL", "10"))

i2c = busio.I2C(board.SCL, board.SDA)

scd = adafruit_scd4x.SCD4X(i2c)
scd.start_periodic_measurement()

bme = bme690.BME690(i2c_addr=0x77)
bme.set_humidity_oversample(bme690.OS_2X)
bme.set_pressure_oversample(bme690.OS_4X)
bme.set_temperature_oversample(bme690.OS_8X)
bme.set_filter(bme690.FILTER_SIZE_3)
bme.set_gas_status(bme690.ENABLE_GAS_MEAS)
bme.set_gas_heater_temperature(320)
bme.set_gas_heater_duration(150)
bme.select_gas_heater_profile(0)

try:
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2)
except AttributeError:
    client = mqtt.Client()
client.connect(BROKER, 1883, keepalive=60)
client.loop_start()

def pub(metric, value):
    topic = f"{PREFIX}/{DEVICE_ID}/{metric}"
    client.publish(topic, str(value), qos=1, retain=True)
    print(f"{topic} = {value}")

print(f"Publishing as DEVICE_ID='{DEVICE_ID}' to {BROKER}:1883 every {INTERVAL}s")
time.sleep(5)

try:
    while True:
        if scd.data_ready:
            pub("co2",         scd.CO2)
            pub("temperature", round(scd.temperature, 2))
            pub("humidity",    round(scd.relative_humidity, 2))
        if bme.get_sensor_data():
            pub("pressure", round(bme.data.pressure, 2))
            if bme.data.heat_stable:
                pub("gas", round(bme.data.gas_resistance, 0))
        time.sleep(INTERVAL)
except KeyboardInterrupt:
    client.loop_stop()
    client.disconnect()
