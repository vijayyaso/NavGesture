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

# Swiping up from the bottom and dragging sideways opens the app list: recents when the drag
# goes right, favourites when it goes left. The sideways travel must pass 40 dp and beat the
# rise, so a wavy swipe up stays a swipe up. A still pause is the phone's own Recents, and a
# side hold opens nothing.
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

# swipe_up_drag <x> <dx> - a short swipe up from the bottom, then a longer drag sideways, then
# lift. The rise only arms the gesture; the sideways travel must beat it.
swipe_up_drag() {
  x="$1" dx="$2" top=$((BOTTOM_Y - 120))
  "${ADB_BIN}" shell input motionevent DOWN "${x}" "${BOTTOM_Y}" >/dev/null
  "${ADB_BIN}" shell input motionevent MOVE "${x}" "${top}" >/dev/null
  "${ADB_BIN}" shell input motionevent MOVE $((x + dx)) "${top}" >/dev/null
  sleep 0.4
  "${ADB_BIN}" shell input motionevent UP $((x + dx)) "${top}" >/dev/null
  sleep 2
}

# hold_edge <x> <y> - press, wait, then lift without moving.
hold_edge() {
  "${ADB_BIN}" shell input motionevent DOWN "$1" "$2" >/dev/null
  sleep 0.7
  "${ADB_BIN}" shell input motionevent UP "$1" "$2" >/dev/null
  sleep 2
}

echo >&2
echo "== a bottom swipe dragged right opens recent apps ==" >&2
open_test_app
reset_log
# 320 px is 122 dp at the emulator's 420 dpi: past the 40 dp trigger and well past the
# 120 px rise.
swipe_up_drag $((WIDTH / 2)) 320
assert_log "the right drag opens recents" "SWITCHER_OPEN bottom recents"
assert_no_log "the drag is not read as Home" "GESTURE_FIRED BOTTOM Home"
assert_no_log "the drag is not read as Recents" "GESTURE_FIRED BOTTOM Recents"
assert_no_log "the drag is not replayed to the app" "REPLAY_DISPATCHED"

echo >&2
echo "== a bottom swipe dragged left opens favourites ==" >&2
open_test_app
reset_log
swipe_up_drag $((WIDTH / 2)) -320
assert_log "the left drag opens favourites" "SWITCHER_OPEN bottom favourites"
assert_no_log "the left drag is not read as Home" "GESTURE_FIRED BOTTOM Home"

echo >&2
echo "== a still pause is the phone's own Recents, not the app list ==" >&2
open_test_app
reset_log
swipe_and_hold $((WIDTH / 2)) "${BOTTOM_Y}" $((WIDTH / 2)) $((HEIGHT - 600)) 0.6
sleep 2
assert_log "the still pause fires Recents" "GESTURE_FIRED BOTTOM Recents"
assert_no_log "the still pause opens no app list" "SWITCHER_OPEN"

echo >&2
echo "== a side hold no longer opens the app list ==" >&2
open_test_app
reset_log
hold_edge 2 "${MID_Y}"
assert_no_log "the left hold opens nothing" "SWITCHER_OPEN"
assert_no_log "the hold is not read as Back" "GESTURE_FIRED LEFT Back"
assert_log "the hold is replayed to the app" "REPLAY_DISPATCHED"

echo >&2
echo "== a quick side swipe is still Back ==" >&2
open_test_app
reset_log
swipe 2 "${MID_Y}" 300 "${MID_Y}" 120
sleep 2
assert_log "the quick swipe fires Back" "GESTURE_FIRED LEFT Back"
assert_no_log "the quick swipe does not open the app list" "SWITCHER_OPEN"

report "APP LIST"
