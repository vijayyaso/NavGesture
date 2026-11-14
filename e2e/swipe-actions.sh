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
# one set to Previous app opens the app used before (Recents without usage access), a far left
# swipe runs its own action while a short one stays Back, a left swipe held still runs its wait
# action while a quick one stays Back, a split left edge runs one action in its top half and
# another in its bottom half, a bottom swipe set to Notifications opens them, and Gesture
# settings warns while no swipe goes Home. Last, slots are picked through the screens, as a user
# would: the short, far and wait left swipes, the split switch, and the far swipe and wait time
# sliders, each put back after.
#
# Uses the debug-only overrides (navgesture_slot_<key>, navgesture_left_split), so it needs the
# debug APK. The exit trap deletes every override, puts back what the screens changed, and
# rebinds, since e2e/lib warm_input needs left = Back.
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

OVERRIDES='navgesture_slot_left_whole_short navgesture_slot_left_whole_long navgesture_slot_left_whole_wait
navgesture_slot_left_top_short navgesture_slot_left_bottom_short navgesture_left_split navgesture_slot_bottom_short'

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

# open_left_edge - Gesture settings, then its Left edge page.
open_left_edge() {
  open_screen row_gesture_settings row_left_edge
  # shellcheck disable=SC2310 # a missing row is reported by the caller
  tap_id row_left_edge || return 1
  sleep 2
}

# pick_left <label> [<row tag>] - the Left edge page, one of its rows (default: Swipe in), then
# that action in the picker.
pick_left() {
  # shellcheck disable=SC2310 # a missing row is reported by the caller
  open_left_edge || return 1
  # shellcheck disable=SC2310 # a missing row is reported by the caller
  tap_id "${2:-row_slot_left_whole_short}" || return 1
  sleep 3
  # shellcheck disable=SC2310 # a missing row is reported by the caller
  choice="$(find_text "$1")" || return 1
  tap_node "${choice}"
  sleep 2
}

# row_shows <label> [<row tag>] - the edge page is back, with that row and that label in one dump.
# The picker has rows with the same labels, so the row's own tag must be there too.
row_shows() {
  screen="$(ui_nodes)"
  printf '%s\n' "${screen}" | grep -q "resource-id=\"${2:-row_slot_left_whole_short}\"" &&
    printf '%s\n' "${screen}" | grep -q "text=\"$1\""
}

# split_left <true|false> - sets the Left edge page's split switch.
split_left() {
  # shellcheck disable=SC2310 # a missing row is reported by the caller
  open_left_edge || return 1
  set_toggle_id switch_split_left "$1"
}

# Set while the UI cases have stored a change, so the trap can put it back if a case stopped early.
picked=''
picked_far=''
picked_wait=''
split_on=''

cleanup() {
  clear_overrides
  "${ADB_BIN}" shell appops set "${PKG}" GET_USAGE_STATS "${usage_mode:-default}" >/dev/null 2>&1 || true
  if [ -n "${picked}" ]; then
    # shellcheck disable=SC2310 # the trap puts Back back as best it can
    pick_left "$(string_res gesture_back_name)" || true
  fi
  # The split goes first: the whole edge's far and wait rows show only while the edge is not split.
  if [ -n "${split_on}" ]; then
    # shellcheck disable=SC2310 # the trap turns the split off as best it can
    split_left false || true
  fi
  if [ -n "${picked_far}" ]; then
    # shellcheck disable=SC2310 # the trap puts Nothing back as best it can
    pick_left "$(string_res corner_none)" row_slot_left_whole_long || true
  fi
  if [ -n "${picked_wait}" ]; then
    # shellcheck disable=SC2310 # the trap puts Nothing back as best it can
    pick_left "$(string_res corner_none)" row_slot_left_whole_wait || true
  fi
  # shellcheck disable=SC2310 # a failed rebind in the trap is not this suite's verdict
  enable_service >/dev/null 2>&1 || true
}
trap cleanup EXIT

geometry
warm_input

# left_until <pattern> <gesture> [<args>] - runs the gesture over the test app, tried again
# while the fresh input path drops it.
left_until() {
  left_pattern="$1"
  shift
  left_try=1
  while [ "${left_try}" -le 4 ]; do
    open_test_app
    reset_log
    "$@"
    sleep 2
    # shellcheck disable=SC2310 # a miss is the retry signal, not an error
    if log_has "${left_pattern}"; then
      return 0
    fi
    left_try=$((left_try + 1))
  done
}

# swipe_left_until <pattern> [<end x> [<y>]] - a left swipe from the edge (default: to x 300, at
# mid height).
swipe_left_until() {
  left_until "$1" swipe 2 "${3:-${MID_Y}}" "${2:-300}" "${3:-${MID_Y}}" 120
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

# Swipe lengths in dp, turned into pixels here: short stays under the 120 dp long line, far
# goes well past it, and neither depends on this emulator's density.
DPI="$("${ADB_BIN}" shell wm density | tr -d '\r' | sed -n 's/.*: *\([0-9]*\)$/\1/p' | tail -1)"
SHORT_X=$((2 + 60 * ${DPI:-420} / 160))
FAR_X=$((2 + 200 * ${DPI:-420} / 160))

echo >&2
echo "== a far left swipe runs the far slot, a short one still goes Back ==" >&2
slot left_whole_long home
swipe_left_until "GESTURE_FIRED LEFT" "${FAR_X}"
assert_log "the far swipe fires Home" "GESTURE_FIRED LEFT Home"
assert_log "the far swipe passed the long line on its way" "PENDING LONG home"
assert_no_log "the far swipe is not Back" "GESTURE_FIRED LEFT Back"
swipe_left_until "GESTURE_FIRED LEFT" "${SHORT_X}"
assert_log "the short swipe still fires Back" "GESTURE_FIRED LEFT Back"
assert_no_log "the short swipe is not Home" "GESTURE_FIRED LEFT Home"
clear_overrides

echo >&2
echo "== a left swipe held still runs the wait slot, a quick one still goes Back ==" >&2
slot left_whole_wait home
# Swipes in past the trigger in one MOVE, holds still about 700 ms (twice the wait time), lifts.
left_until "GESTURE_FIRED LEFT" swipe_and_hold 2 "${MID_Y}" "${SHORT_X}" "${MID_Y}" 0.7
assert_log "the waited swipe fires Home" "GESTURE_FIRED LEFT Home"
assert_log "the wait was reached before the lift" "PENDING WAIT home"
assert_no_log "the waited swipe is not Back" "GESTURE_FIRED LEFT Back"
swipe_left_until "GESTURE_FIRED LEFT" "${SHORT_X}"
assert_log "the quick swipe still fires Back" "GESTURE_FIRED LEFT Back"
assert_no_log "the quick swipe is not Home" "GESTURE_FIRED LEFT Home"
assert_no_log "the quick swipe never reached the wait" "PENDING WAIT"
"${ADB_BIN}" shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
clear_overrides

echo >&2
echo "== a split left edge runs one action in its top half and another in its bottom half ==" >&2
# The strip covers the middle 80 % of the height, so 30 % and 70 % are inside each half.
UPPER_Y=$((HEIGHT * 3 / 10))
LOWER_Y=$((HEIGHT * 7 / 10))
"${ADB_BIN}" shell settings put global navgesture_left_split 1 >/dev/null
"${ADB_BIN}" shell settings put global navgesture_slot_left_top_short home >/dev/null
slot left_bottom_short recents
swipe_left_until "GESTURE_FIRED LEFT" "${SHORT_X}" "${UPPER_Y}"
assert_log "a swipe in the top half fires the top half's action" "GESTURE_FIRED LEFT Home"
assert_no_log "it is not the bottom half's action" "GESTURE_FIRED LEFT Recents"
swipe_left_until "GESTURE_FIRED LEFT" "${SHORT_X}" "${LOWER_Y}"
assert_log "a swipe in the bottom half fires the bottom half's action" "GESTURE_FIRED LEFT Recents"
assert_no_log "it is not the top half's action" "GESTURE_FIRED LEFT Home"
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

# slider_shown [<tag>] - Gesture settings is open (its own row is checked first) and shows that
# slider (default: Far swipe distance).
slider_shown() {
  # shellcheck disable=SC2310 # a missing row is reported as a failed case
  if find_id row_left_edge >/dev/null; then pass "Gesture settings is open"; else fail "Gesture settings is open"; fi
  find_id "${1:-slider_long_swipe}" >/dev/null
}

echo >&2
echo "== the far swipe is picked on its edge page, and only then is its distance shown ==" >&2
none_label="$(string_res corner_none)"
far_name="$(string_res label_long_distance)"
open_screen row_gesture_settings row_left_edge
# shellcheck disable=SC2310 # a shown slider is the failed case
if slider_shown; then
  fail "no Far swipe distance slider while no far swipe is set"
else
  pass "no Far swipe distance slider while no far swipe is set"
fi
# shellcheck disable=SC2310 # a missing page is the failed case
if open_left_edge && row_shows "${none_label}" row_slot_left_whole_long; then
  pass "the Swipe far in row shows Nothing"
else
  fail "the Swipe far in row shows Nothing"
fi
picked_far='1'
# shellcheck disable=SC2310 # a failed pick is the failed case
if pick_left "${home_label}" row_slot_left_whole_long; then
  pass "Swipe far in opens the picker and Home can be picked"
else
  fail "Swipe far in opens the picker and Home can be picked"
fi
# shellcheck disable=SC2310 # a missing row is the failed case
if row_shows "${home_label}" row_slot_left_whole_long; then
  pass "the Swipe far in row shows Home"
else
  fail "the Swipe far in row shows Home"
fi
open_screen row_gesture_settings row_left_edge
# shellcheck disable=SC2310 # a missing slider is the failed case
if slider_shown; then pass "the Far swipe distance slider shows"; else fail "the Far swipe distance slider shows"; fi
# shellcheck disable=SC2310 # a missing label is the failed case
if find_text "${far_name}" >/dev/null; then pass "it is named ${far_name}"; else fail "it is named ${far_name}"; fi

echo >&2
echo "== the split switch shows the two halves, and hides the whole edge's far swipe ==" >&2
split_on='1'
# shellcheck disable=SC2310 # a switch that would not turn on is the failed case
if split_left true; then pass "the split switch turns on"; else fail "the split switch turns on"; fi
for heading in section_top_half section_bottom_half; do
  text="$(string_res "${heading}")"
  # shellcheck disable=SC2310 # a missing heading is the failed case
  if find_text "${text}" >/dev/null; then pass "the page shows ${text}"; else fail "the page shows ${text}"; fi
done
for row in row_slot_left_top_long row_slot_left_bottom_short; do
  # shellcheck disable=SC2310 # a missing row is the failed case
  if find_id "${row}" >/dev/null; then pass "the page shows ${row}"; else fail "the page shows ${row}"; fi
done
open_screen row_gesture_settings row_left_edge
# shellcheck disable=SC2310 # a shown slider is the failed case
if slider_shown; then
  fail "split, the whole edge's far swipe is out of reach and the slider goes"
else
  pass "split, the whole edge's far swipe is out of reach and the slider goes"
fi
# shellcheck disable=SC2310 # a switch that would not turn off is the failed case
if split_left false; then
  split_on=''
  pass "the split switch turns off"
else
  fail "the split switch turns off"
fi
# shellcheck disable=SC2310 # a kept far swipe is the case
if row_shows "${home_label}" row_slot_left_whole_long; then
  pass "the whole edge kept its far swipe"
else
  fail "the whole edge kept its far swipe"
fi

echo >&2
echo "== the far swipe set back to Nothing takes its slider away ==" >&2
# shellcheck disable=SC2310 # a failed pick is the failed case
if pick_left "${none_label}" row_slot_left_whole_long && row_shows "${none_label}" row_slot_left_whole_long; then
  picked_far=''
  pass "the Swipe far in row shows Nothing again"
else
  fail "the Swipe far in row shows Nothing again"
fi
open_screen row_gesture_settings row_left_edge
# shellcheck disable=SC2310 # a shown slider is the failed case
if slider_shown; then
  fail "the Far swipe distance slider is gone"
else
  pass "the Far swipe distance slider is gone"
fi

echo >&2
echo "== the wait swipe is picked on its edge page, and only then is its time shown ==" >&2
wait_row="$(string_res slot_swipe_in_wait)"
wait_name="$(string_res label_wait_time)"
open_screen row_gesture_settings row_left_edge
# shellcheck disable=SC2310 # a shown slider is the failed case
if slider_shown slider_wait; then
  fail "no Wait time slider while no wait swipe is set"
else
  pass "no Wait time slider while no wait swipe is set"
fi
# shellcheck disable=SC2310 # a missing page is the failed case
if open_left_edge && find_text "${wait_row}" >/dev/null && row_shows "${none_label}" row_slot_left_whole_wait; then
  pass "the page has a ${wait_row} row, showing Nothing"
else
  fail "the page has a ${wait_row} row, showing Nothing"
fi
picked_wait='1'
# shellcheck disable=SC2310 # a failed pick is the failed case
if pick_left "${home_label}" row_slot_left_whole_wait; then
  pass "Swipe in and wait opens the picker and Home can be picked"
else
  fail "Swipe in and wait opens the picker and Home can be picked"
fi
# shellcheck disable=SC2310 # a missing row is the failed case
if row_shows "${home_label}" row_slot_left_whole_wait; then
  pass "the Swipe in and wait row shows Home"
else
  fail "the Swipe in and wait row shows Home"
fi
open_screen row_gesture_settings row_left_edge
# shellcheck disable=SC2310 # a missing slider is the failed case
if slider_shown slider_wait; then pass "the Wait time slider shows"; else fail "the Wait time slider shows"; fi
# shellcheck disable=SC2310 # a missing label is the failed case
if find_text "${wait_name}" >/dev/null; then pass "it is named ${wait_name}"; else fail "it is named ${wait_name}"; fi
# shellcheck disable=SC2310 # a failed pick is the failed case
if pick_left "${none_label}" row_slot_left_whole_wait && row_shows "${none_label}" row_slot_left_whole_wait; then
  picked_wait=''
  pass "the Swipe in and wait row shows Nothing again"
else
  fail "the Swipe in and wait row shows Nothing again"
fi
open_screen row_gesture_settings row_left_edge
# shellcheck disable=SC2310 # a shown slider is the failed case
if slider_shown slider_wait; then
  fail "the Wait time slider is gone"
else
  pass "the Wait time slider is gone"
fi

report "SWIPE ACTIONS"
