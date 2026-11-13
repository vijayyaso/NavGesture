#!/bin/sh
#
# Copyright (C) 2026  Vijayyaso
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

# Swipe actions: each swipe runs the action its slot holds. A left swipe set to Home goes Home,
# one set to Previous app opens the app used before (Recents without usage access), a bottom
# swipe set to Notifications opens them, and Gesture settings warns while no swipe goes Home.
# Last, a slot is picked through the screens, as a user would, and put back to Back.
#
# Uses the debug-only slot overrides (navgesture_slot_<key>), so it needs the debug APK. The
# exit trap deletes every override and rebinds, since e2e/lib warm_input needs left = Back.
#
# Expects a device prepared by e2e/device.sh. Run e2e/run.sh for everything.

set -e

# shellcheck source-path=SCRIPTDIR
# shellcheck source=lib
. "$(dirname -- "$0")/lib"

require_adb

if ! "${ADB_BIN}" shell dumpsys package "${PKG}" 2>/dev/null | grep -q 'pkgFlags=.*DEBUGGABLE'; then
  echo "swipe-actions suite needs the debug APK (it sets debug-only overrides); skipped" >&2
  exit 0
fi

OVERRIDES='navgesture_slot_left_whole_short navgesture_slot_bottom_short'

# Previous app's cases change usage access, so the trap puts back what was there.
usage_mode="$("${ADB_BIN}" shell appops get "${PKG}" GET_USAGE_STATS 2>/dev/null | tr -d '\r' |
  sed -n 's/^GET_USAGE_STATS: \([a-z]*\).*/\1/p')"

# slot <key> <action id> - sets one slot's override, then rebinds, as the service reads it at bind.
slot() {
  "${ADB_BIN}" shell settings put global "navgesture_slot_$1" "$2" >/dev/null
  # shellcheck disable=SC2310 # a failed rebind is reported as a failed case, not a script error
  if enable_service; then pass "rebound with $1 = $2"; else fail "rebound with $1 = $2"; fi
}

clear_overrides() {
  for name in ${OVERRIDES}; do
    "${ADB_BIN}" shell settings delete global "${name}" >/dev/null 2>&1 || true
  done
}

# The app screen reads the overrides when it starts, so it is started again in a new task.
restart_ui() {
  "${ADB_BIN}" shell am start -W -f 0x10008000 -n "${PKG}/.ui.MainActivity" >/dev/null 2>&1 || true
  sleep 2
}

# pick_left <label> - Gesture settings, Left edge, its Swipe in row, then that action in the picker.
pick_left() {
  open_screen row_gesture_settings row_left_edge
  # shellcheck disable=SC2310 # a missing row is reported by the caller
  tap_id row_left_edge || return 1
  sleep 2
  # shellcheck disable=SC2310 # a missing row is reported by the caller
  tap_id row_slot_left_whole_short || return 1
  sleep 3
  # shellcheck disable=SC2310 # a missing row is reported by the caller
  choice="$(find_text "$1")" || return 1
  tap_node "${choice}"
  sleep 2
}

# row_shows <label> - the edge page is back, with its Swipe in row and that label in one dump.
# The picker has rows with the same labels, so the row's own tag must be there too.
row_shows() {
  screen="$(ui_nodes)"
  printf '%s\n' "${screen}" | grep -q 'resource-id="row_slot_left_whole_short"' &&
    printf '%s\n' "${screen}" | grep -q "text=\"$1\""
}

# Set when the UI case stored a pick, so the trap can put Back back if the case stopped early.
picked=''

cleanup() {
  clear_overrides
  "${ADB_BIN}" shell appops set "${PKG}" GET_USAGE_STATS "${usage_mode:-default}" >/dev/null 2>&1 || true
  if [ -n "${picked}" ]; then
    # shellcheck disable=SC2310 # the trap puts Back back as best it can
    pick_left "$(string_res gesture_back_name)" || true
  fi
  # shellcheck disable=SC2310 # a failed rebind in the trap is not this suite's verdict
  enable_service >/dev/null 2>&1 || true
}
trap cleanup EXIT

geometry
warm_input

# swipe_left_until <pattern> - a left swipe, tried again while the fresh input path drops it.
swipe_left_until() {
  left_try=1
  while [ "${left_try}" -le 4 ]; do
    open_test_app
    reset_log
    swipe 2 "${MID_Y}" 300 "${MID_Y}" 120
    sleep 2
    # shellcheck disable=SC2310 # a miss is the retry signal, not an error
    if log_has "$1"; then
      return 0
    fi
    left_try=$((left_try + 1))
  done
}

echo >&2
echo "== a left swipe set to Home goes Home ==" >&2
slot left_whole_short home
swipe_left_until "GESTURE_FIRED LEFT"
assert_log "the left swipe fires Home" "GESTURE_FIRED LEFT Home"
assert_no_log "the left swipe is not Back" "GESTURE_FIRED LEFT Back"
clear_overrides

echo >&2
echo "== a left swipe set to Previous app opens the app used before ==" >&2
slot left_whole_short previous_app
"${ADB_BIN}" shell appops set "${PKG}" GET_USAGE_STATS allow >/dev/null
swipe_left_until "GESTURE_FIRED LEFT"
assert_log "the left swipe fires Previous app" "GESTURE_FIRED LEFT previous_app"
sleep 2
previous="$(gesture_log | sed -n 's/.*PREVIOUS_APP \([a-z][a-z0-9_.]*\)$/\1/p' | tail -1)"
front="$(resumed_activity)"
case "${front}" in
  *"${previous:-none}/"*) pass "it opens the app before this one (${previous})" ;;
  *) fail "it opens the app before this one (logged '${previous}', in front: ${front})" ;;
esac

echo >&2
echo "== without usage access, Previous app opens Recents ==" >&2
"${ADB_BIN}" shell appops set "${PKG}" GET_USAGE_STATS ignore >/dev/null
swipe_left_until "GESTURE_FIRED LEFT"
assert_log "with no app list to read, it opens Recents" "PREVIOUS_APP none, opening Recents"
"${ADB_BIN}" shell appops set "${PKG}" GET_USAGE_STATS "${usage_mode:-default}" >/dev/null
"${ADB_BIN}" shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
clear_overrides

echo >&2
echo "== a bottom swipe set to Notifications opens them, not Home ==" >&2
slot bottom_short notifications
open_test_app
reset_log
swipe $((WIDTH / 2)) "${BOTTOM_Y}" $((WIDTH / 2)) $((BOTTOM_Y - 500)) 150
sleep 2
assert_log "the bottom swipe fires its action" "GESTURE_FIRED BOTTOM notifications"
assert_log "the action runs" "SWIPE_ACTION notifications"
assert_no_log "the bottom swipe is not Home" "GESTURE_FIRED BOTTOM Home"
"${ADB_BIN}" shell cmd statusbar collapse >/dev/null 2>&1 || true
sleep 1

echo >&2
echo "== with no swipe going Home, Gesture settings says so ==" >&2
slot bottom_short none
no_home="$(string_res swipe_warning_no_home)"
restart_ui
# shellcheck disable=SC2310 # a missing tile is reported by the assertion below
find_id row_gesture_settings >/dev/null || true
# Loose on purpose: a tile may read its title and line as one node.
# shellcheck disable=SC2310 # a missing line is the failed case
if ui_nodes | grep -q "text=\"[^\"]*${no_home}"; then
  pass "the Gesture settings tile says no swipe goes Home"
else
  fail "the Gesture settings tile says no swipe goes Home"
fi
open_screen row_gesture_settings row_left_edge
# shellcheck disable=SC2310 # a missing node is the failed case
if find_id warning_no_home >/dev/null; then
  pass "the Home warning shows"
else
  fail "the Home warning shows"
fi

echo >&2
echo "== with Home back, the warning goes ==" >&2
clear_overrides
restart_ui
open_screen row_gesture_settings row_left_edge
# The page must be Gesture settings, or a missing warning would prove nothing.
# shellcheck disable=SC2310 # a missing row is the failed case
if find_id row_left_edge >/dev/null; then pass "Gesture settings is open"; else fail "Gesture settings is open"; fi
# shellcheck disable=SC2310 # a missing node is the passing case
if find_id warning_no_home >/dev/null; then
  fail "the Home warning is gone"
else
  pass "the Home warning is gone"
fi
# shellcheck disable=SC2310 # a failed rebind is reported as a failed case, not a script error
if enable_service; then pass "rebound with no slot override"; else fail "rebound with no slot override"; fi

echo >&2
echo "== a swipe's action is picked on its edge page ==" >&2
home_label="$(string_res corner_home)"
back_label="$(string_res gesture_back_name)"
picked='1'
# shellcheck disable=SC2310 # a failed pick is the failed case
if pick_left "${home_label}"; then pass "Home can be picked for the left swipe"; else fail "Home can be picked for the left swipe"; fi
# shellcheck disable=SC2310 # a missing row is the failed case
if row_shows "${home_label}"; then
  pass "the Swipe in row shows Home"
else
  fail "the Swipe in row shows Home"
fi
# shellcheck disable=SC2310 # a failed pick is the failed case
if pick_left "${back_label}"; then pass "Back can be picked again"; else fail "Back can be picked again"; fi
# shellcheck disable=SC2310 # a missing row is the failed case
if row_shows "${back_label}"; then
  picked=''
  pass "the Swipe in row shows Back again"
else
  fail "the Swipe in row shows Back again"
fi

report "SWIPE ACTIONS"
