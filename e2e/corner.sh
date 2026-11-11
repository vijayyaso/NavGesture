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

# Corner shortcuts and the tap-to-pick app list. A swipe up and inward from a bottom corner
# runs that corner's shortcut; a steep swipe from the corner stays Home. In the Tap and Both
# styles the app list stays open after the finger lifts, and a touch outside closes it.
#
# Uses the debug-only overrides (navgesture_left_corner, navgesture_right_corner,
# navgesture_app_list_style), so it needs the debug APK. The emulator reports no usage
# events, so the list shows its "no recent apps" line; opening and closing are what is
# asserted here, and launching from the grid is checked by hand on hardware.
#
# Expects a device prepared by e2e/device.sh. Run e2e/run.sh for everything.

set -e

# shellcheck source-path=SCRIPTDIR
# shellcheck source=lib
. "$(dirname -- "$0")/lib"

require_adb

if ! "${ADB_BIN}" shell dumpsys package "${PKG}" 2>/dev/null | grep -q 'pkgFlags=.*DEBUGGABLE'; then
  echo "corner suite needs the debug APK (it sets debug-only overrides); skipped" >&2
  exit 0
fi

# configure <left> <right> <style> - sets the overrides and rebinds, as they are read at bind.
configure() {
  "${ADB_BIN}" shell settings put global navgesture_left_corner "$1" >/dev/null
  "${ADB_BIN}" shell settings put global navgesture_right_corner "$2" >/dev/null
  "${ADB_BIN}" shell settings put global navgesture_app_list_style "$3" >/dev/null
  # shellcheck disable=SC2310 # a failed rebind is reported as a failed case, not a script error
  if enable_service; then pass "rebound with corners $1/$2, style $3"; else fail "rebound with corners $1/$2, style $3"; fi
}

cleanup() {
  for name in navgesture_left_corner navgesture_right_corner navgesture_app_list_style navgesture_favourites; do
    "${ADB_BIN}" shell settings delete global "${name}" >/dev/null 2>&1 || true
  done
}
trap cleanup EXIT

configure notifications recents_list TAP
geometry
warm_input

# swipe_up_drag <x> <dx> - the app-list trigger: a short rise, then a sideways drag, then lift.
swipe_up_drag() {
  x="$1" dx="$2" top=$((BOTTOM_Y - 120))
  "${ADB_BIN}" shell input motionevent DOWN "${x}" "${BOTTOM_Y}" >/dev/null
  "${ADB_BIN}" shell input motionevent MOVE "${x}" "${top}" >/dev/null
  "${ADB_BIN}" shell input motionevent MOVE $((x + dx)) "${top}" >/dev/null
  sleep 0.4
  "${ADB_BIN}" shell input motionevent UP $((x + dx)) "${top}" >/dev/null
  sleep 2
}

tap_outside() {
  "${ADB_BIN}" shell input tap $((WIDTH / 2)) $((HEIGHT / 3)) >/dev/null
  sleep 1
}

echo >&2
echo "== a left corner diagonal runs its shortcut, not Home ==" >&2
open_test_app
reset_log
corner_swipe 30 1
assert_log "the diagonal is a corner" "CORNER_READY LEFT"
assert_log "the release fires the corner" "GESTURE_FIRED BOTTOM CornerLEFT"
assert_log "the shortcut runs" "CORNER_SHORTCUT notifications"
assert_no_log "the diagonal is not Home" "GESTURE_FIRED BOTTOM Home"
assert_no_log "the diagonal is not replayed" "REPLAY_DISPATCHED"
"${ADB_BIN}" shell cmd statusbar collapse >/dev/null 2>&1 || true
sleep 1

echo >&2
echo "== a steep swipe from the corner is still Home ==" >&2
open_test_app
reset_log
swipe 30 "${BOTTOM_Y}" 45 $((BOTTOM_Y - 500)) 150
sleep 2
assert_log "the steep swipe fires Home" "GESTURE_FIRED BOTTOM Home"
assert_no_log "the steep swipe is not a corner" "CORNER_READY"

echo >&2
echo "== the right corner opens the touch list; a touch outside closes it ==" >&2
open_test_app
reset_log
corner_swipe $((WIDTH - 30)) -1
assert_log "the right diagonal is a corner" "CORNER_READY RIGHT"
assert_log "the list opens for touching" "SWITCHER_OPEN corner recents"
tap_outside
assert_log "a touch outside closes it" "SWITCHER_DISMISSED"
assert_no_log "nothing was launched" "SWITCHER_LAUNCHED"

echo >&2
echo "== Tap style: the list stays open after the finger lifts ==" >&2
open_test_app
reset_log
swipe_up_drag $((WIDTH / 2)) 320
assert_log "the drag opens the list in Tap style" "SWITCHER_OPEN bottom recents TAP"
assert_log "the list waits for a touch" "SWITCHER_TOUCH_MODE"
assert_no_log "the drag is not Home" "GESTURE_FIRED BOTTOM Home"
tap_outside
assert_log "a touch outside closes it" "SWITCHER_DISMISSED"

echo >&2
echo "== a list left alone closes itself ==" >&2
open_test_app
reset_log
swipe_up_drag $((WIDTH / 2)) 320
assert_log "the list waits for a touch" "SWITCHER_TOUCH_MODE"
sleep 11
assert_log "it timed out" "SWITCHER_TIMEOUT"

echo >&2
echo "== Both style: lifting without sliding a step keeps the list open ==" >&2
configure none none BOTH
open_test_app
reset_log
swipe_up_drag $((WIDTH / 2)) 320
assert_log "the drag opens the list in Both style" "SWITCHER_OPEN bottom recents BOTH"
assert_log "no step was slid, so it waits for a touch" "SWITCHER_TOUCH_MODE"
tap_outside
assert_log "a touch outside closes it" "SWITCHER_DISMISSED"

echo >&2
echo "== Tap style with real icons: touching the first app opens it ==" >&2
# Eight launchable apps as favourites, so the grid draws icons rather than a text line.
FAVS="$("${ADB_BIN}" shell cmd package query-activities --brief -a android.intent.action.MAIN \
  -c android.intent.category.LAUNCHER 2>/dev/null | tr -d '\r' | sed -n 's#^ *\([a-z][a-z0-9_.]*\)/.*#\1#p' |
  grep -v -e "^${PKG}\$" -e '^com.android.vending$' | sort -u | head -8 | paste -sd, -)"
FIRST="${FAVS%%,*}"
"${ADB_BIN}" shell settings put global navgesture_favourites "${FAVS}" >/dev/null
configure none none TAP
open_test_app
reset_log
swipe_up_drag $((WIDTH / 2)) -320
assert_log "the left drag opens favourites in Tap style" "SWITCHER_OPEN bottom favourites TAP"
assert_log "the grid lists the eight apps" "SWITCHER_ROWS favourites ${FIRST}"
# First cell centre, from the panel layout: 72 x 84 dp cells, 12 dp padding, the panel's
# bottom 8 dp above the bottom strip (or 40 dp above the screen bottom, if higher).
DPI="$("${ADB_BIN}" shell wm density | tr -d '\r' | sed -n 's/.*: *\([0-9]*\)$/\1/p' | tail -1)"
STRIP_H=$((HEIGHT - BOTTOM_Y + 8))
CELL="$(awk -v w="${WIDTH}" -v h="${HEIGHT}" -v dpi="${DPI}" -v strip="${STRIP_H}" 'BEGIN {
  d = dpi / 160; n = 8
  cols = int((w / d - 28) / 72); if (cols > n) cols = n; if (cols < 1) cols = 1
  rows = int((n + cols - 1) / cols); if (rows > 3) rows = 3
  gap = strip + int(8 * d); if (gap < int(40 * d)) gap = int(40 * d)
  top = h - gap - (rows * 84 + 24) * d
  left = (w - (cols * 72 + 24) * d) / 2
  printf "%d %d", left + 48 * d, top + 54 * d }')"
# shellcheck disable=SC2086 # two numbers, split on purpose
"${ADB_BIN}" shell input tap ${CELL} >/dev/null
sleep 2
assert_log "touching the first icon opens it" "SWITCHER_LAUNCHED ${FIRST}"
"${ADB_BIN}" shell settings delete global navgesture_favourites >/dev/null 2>&1 || true

echo >&2
echo "== with no corner shortcut, a corner diagonal is not a corner ==" >&2
open_test_app
reset_log
corner_swipe 30 1
assert_no_log "no corner without a shortcut" "CORNER_READY"

report "CORNERS AND TAP LIST"
