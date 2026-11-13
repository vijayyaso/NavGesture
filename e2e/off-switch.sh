#!/bin/sh
#
# Copyright (C) 2026  Henrique Almeida <me@h3nc4.com>
#
# This file is part of HyperGesture.
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.

# The user's own switch-off: the Home switch and a tap on the tile both switch a running service
# off at once, and nothing turns it back on by itself. Needs a device from e2e/device.sh, and
# binds the service again at the end.

set -e

# shellcheck source-path=SCRIPTDIR
# shellcheck source=lib
. "$(dirname -- "$0")/lib"

require_adb

TILE="${PKG}/.setup.GestureTileService"

listed() {
  "${ADB_BIN}" shell settings get secure enabled_accessibility_services 2>/dev/null |
    tr -d '\r' | grep -q "${PKG}/"
}

# wait_off - true once the service is neither bound nor on the Accessibility list.
wait_off() {
  off_try=1
  while [ "${off_try}" -le 8 ]; do
    # shellcheck disable=SC2310 # false predicates are the retry signal
    if ! service_bound && ! listed; then return 0; fi
    sleep 1
    off_try=$((off_try + 1))
  done
  return 1
}

service_on="$(string_res status_working)"
service_off="$(string_res status_off)"

# With the grant the app could turn itself back on, which is what must not happen.
"${ADB_BIN}" shell pm grant "${PKG}" android.permission.WRITE_SECURE_SETTINGS >/dev/null

echo >&2
echo "== the Home switch turns the gestures off at once ==" >&2
# shellcheck disable=SC2310 # reported below
service_bound || enable_service || true
open_home
# shellcheck disable=SC2310 # an unread status is reported by the check below
shown="$(text_of_id service_status || true)"
if [ "${shown}" = "${service_on}" ]; then pass "home reads on"; else fail "home reads on (shows '${shown:-nothing}')"; fi
# shellcheck disable=SC2310 # a tap that failed is reported by the wait below
set_toggle_id status_switch false || true
# shellcheck disable=SC2310 # reported here
if wait_off; then pass "the switch turned the service off"; else fail "the switch turned the service off"; fi
# shellcheck disable=SC2310 # an unread status is reported by the check below
shown="$(text_of_id service_status || true)"
if [ "${shown}" = "${service_off}" ]; then pass "home reads off"; else fail "home reads off (shows '${shown:-nothing}')"; fi

echo >&2
echo "== nothing turns it back on: the app opening and Control Center both run the heal ==" >&2
"${ADB_BIN}" shell input keyevent KEYCODE_HOME >/dev/null
sleep 1
open_home
"${ADB_BIN}" shell cmd statusbar expand-settings >/dev/null 2>&1 || true
sleep 3
"${ADB_BIN}" shell cmd statusbar collapse >/dev/null 2>&1 || true
sleep 2
# shellcheck disable=SC2310 # reported here
if ! service_bound && ! listed; then pass "the service stayed off"; else fail "the service stayed off"; fi

echo >&2
echo "== a tap on the tile turns a running service off ==" >&2
# shellcheck disable=SC2310 # reported below
enable_service || true
# shellcheck disable=SC2310 # reported here
if service_bound; then pass "the service is back on"; else fail "the service is back on"; fi
# The app is left behind first, so a tap that opened it would show.
"${ADB_BIN}" shell input keyevent KEYCODE_HOME >/dev/null
sleep 1
# click-tile reaches only a tile that is in the panel, and SystemUI's copy can go stale.
"${ADB_BIN}" shell cmd statusbar remove-tile "${TILE}" >/dev/null 2>&1 || true
sleep 1
"${ADB_BIN}" shell cmd statusbar add-tile "${TILE}" >/dev/null 2>&1 || true
sleep 2
tile_try=1
tile_off=''
while [ "${tile_try}" -le 3 ]; do
  "${ADB_BIN}" shell cmd statusbar click-tile "${TILE}" >/dev/null 2>&1 || true
  # shellcheck disable=SC2310 # false is the retry signal
  if wait_off; then
    tile_off='1'
    break
  fi
  tile_try=$((tile_try + 1))
done
if [ -n "${tile_off}" ]; then pass "the tile turned the service off (tap ${tile_try})"; else fail "the tile turned the service off"; fi
# shellcheck disable=SC2310 # reported here
if resumed_activity | grep -q "${PKG}/"; then fail "the tile tap did not open the app"; else pass "the tile tap did not open the app"; fi

echo >&2
echo "== the service binds again for the suites that follow ==" >&2
"${ADB_BIN}" shell cmd statusbar collapse >/dev/null 2>&1 || true
# shellcheck disable=SC2310 # reported here
if enable_service; then pass "bound again"; else fail "bound again"; fi

report "OFF SWITCH"
