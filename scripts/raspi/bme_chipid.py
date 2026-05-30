#!/usr/bin/env python3
"""Read raw BME69x identity registers to determine which chip we have."""

from smbus2 import SMBus

ADDR = 0x77
with SMBus(1) as bus:
    chip_id = bus.read_byte_data(ADDR, 0xD0)
    variant = bus.read_byte_data(ADDR, 0xF0)
    print(f"chip_id (0xD0) = 0x{chip_id:02X}")
    print(f"variant_id (0xF0) = 0x{variant:02X}")
    print()
    print("Reference:")
    print("  BME680 -> chip_id 0x61, variant 0x00")
    print("  BME688 -> chip_id 0x61, variant 0x01")
    print("  BME690 -> chip_id 0x61, variant 0x02 (or different)")
