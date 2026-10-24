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

# A still finger on a side edge opens the app list. A bottom sideways swipe does nothing.
#
# Only the recognizer and panel opening are asserted here: this emulator reports no usage
# events, so the launch itself is proven by hand on hardware.
#
# Expects a device prepared by e2e/device.sh. Run e2e/run.sh for everything.

set -e

# shellcheck source-path=SCRIPTDIR
# shellcheck source=lib
. "$(dirname -- "$0")/lib"

require_adb
geometry
warm_input

# hold_edge <x> <y> - press, wait past the hold delay, then lift without moving.
hold_edge() {
  "${ADB_BIN}" shell input motionevent DOWN "$1" "$2" >/dev/null
  sleep 0.7
  "${ADB_BIN}" shell input motionevent UP "$1" "$2" >/dev/null
  sleep 2
}

# A drift past the system's 8 dp touch slop must still count as a resting finger.
density="$("${ADB_BIN}" shell wm density 2>/dev/null | tr -d '\r' |
  awk -F': *' 'NF > 1 {print $2}' | tail -1)"
[ -n "${density}" ] || density=420
drift=$((density * 12 / 160))
[ "${drift}" -gt 1 ] || drift=12

echo >&2
echo "== a still finger on the left edge opens recent apps ==" >&2
open_test_app
reset_log
hold_edge 2 "${MID_Y}"
assert_log "the left hold opens the app list" "SWITCHER_OPEN LEFT"
assert_no_log "the hold is not read as Back" "GESTURE_FIRED LEFT Back"
assert_no_log "the hold is not replayed to the app" "REPLAY_DISPATCHED"

echo >&2
echo "== a still finger on the right edge opens favourites ==" >&2
open_test_app
reset_log
hold_edge "${RIGHT_X}" "${MID_Y}"
assert_log "the right hold opens the app list" "SWITCHER_OPEN RIGHT"
assert_no_log "the right hold is not read as Back" "GESTURE_FIRED RIGHT Back"

echo >&2
echo "== a small resting drift still opens the app list ==" >&2
open_test_app
reset_log
"${ADB_BIN}" shell input motionevent DOWN 2 "${MID_Y}" >/dev/null
"${ADB_BIN}" shell input motionevent MOVE $((2 + drift)) "${MID_Y}" >/dev/null
sleep 0.7
"${ADB_BIN}" shell input motionevent UP $((2 + drift)) "${MID_Y}" >/dev/null
sleep 2
assert_log "a 12 dp drift still opens the app list" "SWITCHER_OPEN LEFT"
assert_no_log "the drift is not read as Back" "GESTURE_FIRED LEFT Back"

echo >&2
echo "== a sideways swipe along the bottom no longer switches apps ==" >&2
open_test_app
reset_log
swipe 300 "${BOTTOM_Y}" 800 "${BOTTOM_Y}" 120
sleep 2
assert_no_log "the sideways bottom swipe fires no bottom gesture" "GESTURE_FIRED BOTTOM"
assert_log "the sideways bottom swipe is replayed to the app" "REPLAY_DISPATCHED"

echo >&2
echo "== a quick side swipe is still Back, not the app list ==" >&2
open_test_app
reset_log
swipe 2 "${MID_Y}" 300 "${MID_Y}" 120
sleep 2
assert_log "the quick swipe fires Back" "GESTURE_FIRED LEFT Back"
assert_no_log "the quick swipe does not open the app list" "SWITCHER_OPEN"

report "EDGE APP LIST"
