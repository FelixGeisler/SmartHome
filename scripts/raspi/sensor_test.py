#!/usr/bin/env python3
"""Quick read test for SCD41 (CO2) and BME690 (T/RH/P/gas) on Pi I2C bus 1."""

import time
import board
import busio
import adafruit_scd4x
import bme690

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

print("Warming up (~5 s for SCD41 first sample)...")
time.sleep(5)

try:
    while True:
        parts = []
        if scd.data_ready:
            parts.append(f"CO2={scd.CO2} ppm")
            parts.append(f"T_scd={scd.temperature:.1f} C")
            parts.append(f"RH_scd={scd.relative_humidity:.1f} %")
        else:
            parts.append("SCD41=not_ready")

        if bme.get_sensor_data():
            d = bme.data
            parts.append(f"T_bme={d.temperature:.1f} C")
            parts.append(f"RH_bme={d.humidity:.1f} %")
            parts.append(f"P={d.pressure:.1f} hPa")
            if d.heat_stable:
                parts.append(f"gas={d.gas_resistance:.0f} ohm")
            else:
                parts.append("gas=warming")
        else:
            parts.append("BME690=no_data")

        print("  ".join(parts), flush=True)
        time.sleep(5)
except KeyboardInterrupt:
    print("\nbye")
