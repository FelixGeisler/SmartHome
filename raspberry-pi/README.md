# Raspberry Pi sensor node (reference)

A working reference for an MQTT sensor node: a Raspberry Pi running Mosquitto and a Python
publisher that reads a BME280 (temperature, humidity, pressure) and an SCD41 (CO₂) over I²C and
publishes to the broker.

For the general integration steps (running a broker, pointing the hub at it, registering the
device), see the **MQTT sensors** guide
(`docs/guide/modules/ROOT/pages/mqtt-sensors.adoc`). This file covers only what's specific to
this node.

## Wiring (shared I²C bus)

Both sensors share the I²C pins; their addresses (BME280 `0x76`, SCD41 `0x62`) keep them apart.

| Sensor pin | Pi pin | Signal |
|-----------|--------|--------|
| VCC / VIN | 1 | 3V3 |
| GND | 6 | GND |
| SCL | 5 | GPIO3 (SCL1) |
| SDA | 3 | GPIO2 (SDA1) |

Power the sensors from 3V3. Confirm they're detected with `i2cdetect -y 1` (expect `76` and `62`).

## Run

```bash
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
cp sensor-node.env.example sensor-node.env   # set NODE_ID, broker, etc.
python sensor_node.py
```

Install on boot with the provided `sensor-node.service` (systemd). Set `ENABLE_CO2=false` for a
BME280-only node.

## Files

- `sensor_node.py` — reads the sensors over I²C and publishes readings over MQTT.
- `requirements.txt` — Python dependencies.
- `sensor-node.env.example` — configuration template (copy to `sensor-node.env`).
- `sensor-node.service` — systemd unit to run the publisher on boot.
- `mosquitto.conf` — example broker configuration.
