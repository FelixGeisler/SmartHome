# Raspberry Pi sensor node (reference)

A working reference for an MQTT sensor node: a Raspberry Pi running Mosquitto and a Python
publisher that reads a BME690 (temperature, humidity, pressure) and an SCD41 (CO₂) over I²C and
publishes to the broker.

For the general integration steps (running a broker, pointing the hub at it, registering the
device), see the **MQTT sensors** guide
(`docs/guide/modules/ROOT/pages/mqtt-sensors.adoc`). This file covers only what's specific to
this node.

## Wiring (shared I²C bus)

Both sensors share the I²C pins; their addresses (BME690 `0x77`/`0x76`, SCD41 `0x62`) keep them apart.

| Sensor pin | Pi pin | Signal |
|-----------|--------|--------|
| VCC / VIN | 1 | 3V3 |
| GND | 6 | GND |
| SCL | 5 | GPIO3 (SCL1) |
| SDA | 3 | GPIO2 (SDA1) |

Power the sensors from 3V3. Confirm they're detected with `i2cdetect -y 1` (expect `77` (or `76`) and `62`).

## Run

```bash
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
cp sensor-node.env.example sensor-node.env   # set NODE_ID, broker, etc.
python sensor_node.py
```

Set `ENABLE_CO2=false` in `sensor-node.env` for a node without a CO2 sensor.

## Install on boot

Once the venv and `sensor-node.env` exist, install the systemd service with the helper:

```bash
bash install.sh
```

It fills in this Pi's login user and the actual node directory, then enables and starts the unit —
so it works whatever the Pi's username is (`pi`, `raspberry`, …) and wherever you copied the node.
The shipped `sensor-node.service` keeps `pi`/`/home/pi/...` only as placeholders; don't run it
unedited.

On the Pi that also runs the broker, enable Mosquitto on boot too:

```bash
sudo systemctl enable --now mosquitto
```

## Files

- `sensor_node.py` — reads the sensors over I²C and publishes readings over MQTT.
- `requirements.txt` — Python dependencies.
- `sensor-node.env.example` — configuration template (copy to `sensor-node.env`).
- `sensor-node.service` — systemd unit to run the publisher on boot.
- `install.sh` — installs that unit for this Pi (fills in the user and node directory).
- `mosquitto.conf` — example broker configuration.
