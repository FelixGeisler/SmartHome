# Solakon Modbus relay (reference)

A reference for reaching a Solakon (FoxESS) inverter over Modbus TCP when it is not on the same
network as the hub, for example when the inverter is far from any Ethernet drop. A Raspberry Pi sits
next to the inverter, gives it a network connection, and relays Modbus TCP so the hub can poll it.

This is only needed when the inverter cannot be plugged straight into your LAN. If it is already on
your network with Modbus TCP enabled, point the hub at it directly and skip this (see the Solakon
steps in `docs/guide/modules/ROOT/pages/adding-devices.adoc`).

## How it fits together

- The inverter's wired Ethernet plugs into the Pi. The Pi shares its own uplink (Wi-Fi or a second
  interface) to the inverter, so the inverter still reaches the internet for the Solakon app.
- The Pi runs a small Modbus TCP relay on port 502 that forwards to the inverter. The hub connects
  to the Pi's address on port 502, and the relay passes the requests through.
- The two paths are independent: the inverter's internet is plain network sharing, and Modbus is the
  relay service on top, so restarting or replacing the relay never affects the inverter's internet.

## Share the Pi's network to the inverter

Give the inverter-facing interface a private network and share the Pi's uplink to it. With
NetworkManager this is the "Shared to other computers" IPv4 method on that interface, which sets up
the address, NAT, and a small DHCP server. The inverter then gets an address on that private network
and reaches the internet through the Pi.

## Run the Modbus relay

`solakon-relay.service` runs a `socat` relay from the Pi's port 502 to the inverter. Set the
inverter's address in the unit, then install it:

```bash
sudo cp solakon-relay.service /etc/systemd/system/
sudoedit /etc/systemd/system/solakon-relay.service   # set the inverter's address
sudo systemctl enable --now solakon-relay
```

Then point the hub's Solakon panel at the Pi's LAN address (not the inverter's), with the standard
Modbus port 502 and unit id 1.

### Why the relay is hardened

Solakon/FoxESS inverters accept only about one Modbus client at a time, and a connection that is not
closed cleanly can hold that single slot until it is reset. The `-T 15` option makes `socat` drop
any connection idle for 15 seconds, so a stalled connection frees the slot on its own instead of
wedging until a reboot. `connect-timeout` and `keepalive` drop dead peers quickly.

### A sturdier alternative

If the relay still wedges, replace `socat` with a Modbus-aware proxy such as `modbus-proxy`, which
keeps a single persistent connection to the inverter and serializes clients over it, reconnecting
automatically. That removes the "many clients, one slot" problem entirely.

## Files

- `solakon-relay.service`: systemd unit running the `socat` Modbus TCP relay to the inverter.
