#!/usr/bin/env bash
# Installs the sensor-node systemd service for THIS Pi, filling in the current user and the actual
# directory these files live in — so it works regardless of the login name (pi, raspberry, …) or
# where the node was copied. Run it from the node directory after creating the venv and the env file
# (see README.md "Run"):  bash install.sh
set -euo pipefail

dir="$(cd "$(dirname "$0")" && pwd)"

if [ ! -x "$dir/.venv/bin/python" ]; then
  echo "Missing $dir/.venv — create it first: python3 -m venv .venv && .venv/bin/pip install -r requirements.txt" >&2
  exit 1
fi
if [ ! -f "$dir/sensor-node.env" ]; then
  echo "Missing $dir/sensor-node.env — create it first: cp sensor-node.env.example sensor-node.env (then edit)" >&2
  exit 1
fi

# Substitute the placeholder user and path from the shipped unit with this Pi's real values.
sed -e "s|^User=.*|User=$USER|" \
    -e "s|/home/pi/smarthome-sensor-node|$dir|g" \
    "$dir/sensor-node.service" | sudo tee /etc/systemd/system/sensor-node.service >/dev/null

sudo systemctl daemon-reload
sudo systemctl enable --now sensor-node
systemctl status sensor-node --no-pager
