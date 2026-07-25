#!/usr/bin/env bash
# vehplayer - CGNAT reachability test AP
#
# Brings up a WiFi access point on a Raspberry Pi that hands out addresses in
# CGNAT / RFC6598 space (100.64.0.0/10) instead of the usual RFC1918, and
# serves a trivial /ping on it.
#
# WHY THIS EXISTS (docs/REACHABILITY_RETHINK.md): the Tesla browser is MEASURED
# to refuse RFC1918 (10/8, 172.16/12, 192.168/16) on both TCP and UDP, and the
# car has no IPv6 at all. 100.64.0.0/10 is NOT RFC1918, so the measured filter
# does not obviously cover it, but it has never been put in front of the car:
# the mechanism that was meant to deliver it (Android VpnService) died in the
# kernel before a single packet arrived. This script is the cheapest possible
# way to finally ask the question, and the answer decides whether the whole
# hardware direction in docs/PIVOT_HARDWARE.md is real.
#
# No phone, no internet and no vehplayer app are involved. The car just has to
# join this AP and load one URL.
#
# Usage:
#   sudo ./ap-cgnat.sh up      # start the AP + test server
#   sudo ./ap-cgnat.sh down    # tear it all down again
#   sudo ./ap-cgnat.sh status
#
# !! READ THIS FIRST !!
# If you are SSH'd into the Pi over WiFi, this WILL cut you off, because it
# reconfigures the wireless interface. Use ethernet, a keyboard and monitor, or
# the serial console. The script refuses to run over a WiFi SSH session unless
# you pass --force.

set -euo pipefail

SSID="${VEHPLAYER_SSID:-vehplayer-test}"
PSK="${VEHPLAYER_PSK:-vehplayer1234}"   # WPA2 needs 8+ chars
IFACE="${VEHPLAYER_IFACE:-wlan0}"
CON_NAME="vehplayer-cgnat"
# .1 of a /24 carved out of 100.64.0.0/10. Any address in 100.64.0.0 ..
# 100.127.255.255 is inside RFC6598; this one is easy to type on a car screen.
AP_ADDR="100.64.0.1"
AP_CIDR="${AP_ADDR}/24"
PORT="${VEHPLAYER_PORT:-8080}"
WEBROOT="/run/vehplayer-cgnat"
PIDFILE="/run/vehplayer-cgnat.pid"

log() { printf '\033[1;33m==\033[0m %s\n' "$*"; }
err() { printf '\033[1;31m!!\033[0m %s\n' "$*" >&2; }

require_root() {
  [ "$(id -u)" -eq 0 ] || { err "run with sudo"; exit 1; }
}

# Losing your own SSH session halfway through reconfiguring the radio is a
# genuinely miserable way to find out this script touches wlan0.
guard_ssh_over_wifi() {
  [ "${1:-}" = "--force" ] && return 0
  if [ -n "${SSH_CONNECTION:-}" ]; then
    local client_ip route_iface
    client_ip=$(awk '{print $1}' <<<"$SSH_CONNECTION")
    route_iface=$(ip -o route get "$client_ip" 2>/dev/null | grep -o 'dev [^ ]*' | awk '{print $2}' || true)
    if [ "$route_iface" = "$IFACE" ]; then
      err "You are SSH'd in over $IFACE, which this script is about to reconfigure."
      err "You will lose this session. Use ethernet or a console, or re-run with --force."
      exit 1
    fi
  fi
}

check_deps() {
  command -v nmcli >/dev/null || {
    err "nmcli not found. This script targets Raspberry Pi OS Bookworm or newer,"
    err "which manages the network with NetworkManager."
    err "On an older release, either upgrade or set up hostapd + dnsmasq by hand"
    err "with the same address ($AP_CIDR) - the test is what matters, not the tooling."
    exit 1
  }
  command -v python3 >/dev/null || { err "python3 not found"; exit 1; }
  ip link show "$IFACE" >/dev/null 2>&1 || {
    err "interface $IFACE not found. Set VEHPLAYER_IFACE to the right one:"
    ip -o link show | awk -F': ' '{print "    " $2}'
    exit 1
  }
}

up() {
  check_deps
  log "creating AP '$SSID' on $IFACE at $AP_CIDR"

  nmcli connection delete "$CON_NAME" >/dev/null 2>&1 || true
  nmcli connection add type wifi ifname "$IFACE" mode ap con-name "$CON_NAME" ssid "$SSID" >/dev/null

  # 'shared' makes NetworkManager run its own DHCP for us; combined with an
  # explicit ipv4.addresses it hands clients addresses out of THAT subnet,
  # which is the entire point of this exercise.
  nmcli connection modify "$CON_NAME" \
    802-11-wireless.band bg \
    802-11-wireless.channel 6 \
    wifi-sec.key-mgmt wpa-psk \
    wifi-sec.psk "$PSK" \
    ipv4.method shared \
    ipv4.addresses "$AP_CIDR" \
    ipv6.method disabled >/dev/null

  nmcli connection up "$CON_NAME" >/dev/null
  log "AP is up"

  mkdir -p "$WEBROOT"
  printf 'pong\n' > "$WEBROOT/ping"
  cat > "$WEBROOT/index.html" <<HTML
<!doctype html><meta charset=utf-8>
<meta name=viewport content="width=device-width,initial-scale=1">
<title>vehplayer CGNAT test</title>
<body style="background:#14120F;color:#F5F0E6;font:600 28px system-ui;padding:8vw">
<p style="color:#5dbb63;font-size:12vw;margin:0">IT LOADED</p>
<p>This page came from <b>$AP_ADDR</b>, which is CGNAT space (100.64.0.0/10),
not RFC1918.</p>
<p style="color:#948E82;font-size:20px">If you are reading this on the car
screen, the car accepts an address the phone could never present, and the
hardware direction in docs/PIVOT_HARDWARE.md is validated.</p>
</body>
HTML

  ( cd "$WEBROOT" && setsid python3 -m http.server "$PORT" --bind 0.0.0.0 \
      >/var/log/vehplayer-cgnat.log 2>&1 & echo $! > "$PIDFILE" )
  sleep 1

  log "test server listening on port $PORT"
  echo
  echo "  1. On the car: join WiFi  SSID '$SSID'  password '$PSK'"
  echo "  2. On the car browser open:  http://$AP_ADDR:$PORT/"
  echo "  3. CONTROL FIRST: do the same from a laptop or phone on this AP."
  echo "     If the control fails, the Pi is the problem, not the car."
  echo
  echo "  loads      -> CGNAT is accepted. This is the answer we want."
  echo "  refused    -> Tesla blocks more than RFC1918. Photograph the exact"
  echo "                error code, the difference matters."
  echo
  echo "  tear down with: sudo $0 down"
}

down() {
  log "tearing down"
  if [ -f "$PIDFILE" ]; then
    kill "$(cat "$PIDFILE")" 2>/dev/null || true
    rm -f "$PIDFILE"
  fi
  pkill -f "http.server $PORT" 2>/dev/null || true
  nmcli connection down "$CON_NAME" >/dev/null 2>&1 || true
  nmcli connection delete "$CON_NAME" >/dev/null 2>&1 || true
  rm -rf "$WEBROOT"
  log "done, $IFACE is back to normal"
}

status() {
  nmcli -f NAME,DEVICE,STATE connection show --active | grep -E "NAME|$CON_NAME" || echo "AP not active"
  ip -4 addr show "$IFACE" | grep -E 'inet ' || true
  if [ -f "$PIDFILE" ] && kill -0 "$(cat "$PIDFILE")" 2>/dev/null; then
    echo "test server: running (pid $(cat "$PIDFILE"), port $PORT)"
  else
    echo "test server: not running"
  fi
}

require_root
case "${1:-}" in
  up)     guard_ssh_over_wifi "${2:-}"; up ;;
  down)   down ;;
  status) status ;;
  *)      echo "usage: sudo $0 {up|down|status} [--force]"; exit 1 ;;
esac
