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
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.

# Live practice on the real edges. With the service working, "Try the gestures" opens a page
# where the real strips and the real recogniser judge each swipe, but nothing runs: the overlay
# reports the gesture to the page instead (PRACTICE_CAUGHT, PRACTICE_MISSED). Every "nothing ran"
# check reads logcat and has a control, the same swipe with practice closed, which must run for
# real (ACTION_PERFORMED or SWITCHER_LAUNCHED). Every case in practice also checks that the
# backstop logged nothing (PRACTICE_BLOCKED): the report layer must catch each gesture first.
#
# Each uiautomator dump can unbind the service for a moment, so the strips are gone for that
# moment. After every dump the suite waits for the strip and one second more before a swipe.
#
# Cases marked "debug" set debug-only overrides and are skipped on a release APK; the others
# also run with -p on the release APK, which proves the markers survive R8. The exit trap
# deletes every override set here and rebinds.
#
# Expects a device prepared by e2e/device.sh, with the service on. Run e2e/run.sh for everything.

# Every predicate below is a test result that pass or fail reports, never an error to exit on.
# shellcheck disable=SC2310,SC2312

set -e

# shellcheck source-path=SCRIPTDIR
# shellcheck source=lib
. "$(dirname -- "$0")/lib"

require_adb

OVERRIDES='navgesture_force_drawn_demo navgesture_slot_left_whole_long navgesture_favourites
navgesture_app_list_style navgesture_left_corner navgesture_slot_right_whole_short
navgesture_slot_bottom_short navgesture_slot_bottom_pause navgesture_edge_app_list'

clear_overrides() {
  for name in ${OVERRIDES}; do
    "${ADB_BIN}" shell settings delete global "${name}" >/dev/null 2>&1 || true
  done
}

cleanup() {
  clear_overrides
  # A failed rebind in the trap is not this suite's verdict.
  enable_service >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

# A demo run stopped early may have left the drawn demo forced.
"${ADB_BIN}" shell settings delete global navgesture_force_drawn_demo >/dev/null 2>&1 || true

geometry
warm_input

DEBUG=''
if "${ADB_BIN}" shell dumpsys package "${PKG}" 2>/dev/null | grep -q 'pkgFlags=.*DEBUGGABLE'; then
  DEBUG='1'
else
  echo "   release APK: the cases that need debug-only overrides are skipped" >&2
fi

DENSITY="$("${ADB_BIN}" shell wm density | tr -d '\r' | awk -F': *' '{print $2}' | tail -1)"
dp() {
  echo $(($1 * DENSITY / 160))
}

# The strips are back after a dump, and the input path has caught up.
settle() {
  wait_strip 0 || echo "   the bottom strip did not settle" >&2
  sleep 1
}

back_swipe() {
  swipe 2 "${MID_Y}" 300 "${MID_Y}" 120
  sleep 2
}

home_swipe() {
  swipe $((WIDTH / 2)) "${BOTTOM_Y}" $((WIDTH / 2)) $((HEIGHT - 600)) 120
  sleep 2
}

recents_swipe() {
  swipe_and_hold $((WIDTH / 2)) "${BOTTOM_Y}" $((WIDTH / 2)) $((HEIGHT - 600)) 0.6
  sleep 2
}

# Under the 24 dp trigger after the 1 s arm window, so it arms too late (TOO_SLOW_TO_ARM). Outside
# practice it is replayed to the app as a swipe.
slow_swipe() {
  swipe 2 "${MID_Y}" $((2 + $(dp 20))) "${MID_Y}" 2000
  sleep 2
}

# Past the 120 dp long line.
long_swipe() {
  swipe 2 "${MID_Y}" $((2 + $(dp 200))) "${MID_Y}" 150
  sleep 2
}

practice_shown() {
  ui_nodes | grep -q 'resource-id="practice_page"'
}

# step_text - "Step n of m" as the page shows it now, or nothing.
step_text() {
  ui_nodes | grep -m1 'resource-id="practice_step"' | sed -n 's/.* text="\([^"]*\)".*/\1/p'
}

# practice_step_reaches <n> - waits up to 5 s for "Step <n> of <m>": a pass shows for 900 ms first.
practice_step_reaches() {
  waited=0
  while [ "${waited}" -lt 5 ]; do
    if step_text | grep -q " $1 of [0-9]*\$"; then
      return 0
    fi
    sleep 1
    waited=$((waited + 1))
  done
  return 1
}

# open_practice - opens practice from the Home row, at step 1, and waits for the strips.
open_practice() {
  open_screen row_demo practice_page
  if practice_step_reaches 1; then pass "practice opens at step 1"; else fail "practice opens at step 1"; fi
  settle
}

# close_practice - taps Close, or Done when the swipe passed the last step and the Finish card
# shows, and waits for the strips after the dump.
close_practice() {
  screen="$(ui_nodes)"
  node="$(printf '%s\n' "${screen}" | grep -m1 'resource-id="practice_close"' || true)"
  [ -n "${node}" ] || node="$(printf '%s\n' "${screen}" | grep -m1 'resource-id="demo_done"' || true)"
  tap_node "${node}"
  sleep 1
  if practice_shown; then fail "Close closes practice"; else pass "Close closes practice"; fi
  settle
}

# rebind_with <description> - the overrides are read at bind, so rebind before practice opens.
rebind_with() {
  if enable_service && service_bound; then pass "rebound with $1"; else fail "rebound with $1"; fi
  settle
}

# advance_to <n> - from the current step to step <n>. Steps 1 to 3 are Back, Home and Recents in
# every plan here, so they pass with their own swipe; any other step gets two taps on the left
# strip (two tries) and Skip this step. The taps log ACTION_PERFORMED replay_tap, so each case
# runs reset_log after this.
advance_to() {
  guard=0
  while [ "${guard}" -lt 8 ]; do
    now="$(step_text | sed -n 's/^[^0-9]*\([0-9]*\) of.*/\1/p')"
    if [ -z "${now}" ]; then
      fail "advance to step $1 (no step shown)"
      return 0
    fi
    if [ "${now}" -ge "$1" ]; then
      return 0
    fi
    settle
    case "${now}" in
      1) back_swipe ;;
      2) home_swipe ;;
      3) recents_swipe ;;
      *)
        "${ADB_BIN}" shell input tap 2 "${MID_Y}" >/dev/null
        sleep 1
        "${ADB_BIN}" shell input tap 2 "${MID_Y}" >/dev/null
        sleep 1
        tap_node "$(ui_nodes | grep -m1 'resource-id="practice_skip_step"' || true)"
        ;;
    esac
    if ! practice_step_reaches $((now + 1)); then
      fail "advance from step ${now} to step $((now + 1))"
      return 0
    fi
    guard=$((guard + 1))
  done
}

echo >&2
echo "== 1. control: Back, Home and Recents run for real with practice closed ==" >&2
open_screen row_troubleshooting
settle
reset_log
back_swipe
assert_log "control: a left swipe runs Back" "ACTION_PERFORMED Back"
reset_log
home_swipe
assert_log "control: a bottom swipe runs Home" "ACTION_PERFORMED Home"
open_screen row_troubleshooting
settle
reset_log
recents_swipe
assert_log "control: a held bottom swipe runs Recents" "ACTION_PERFORMED Recents"
"${ADB_BIN}" shell input keyevent KEYCODE_BACK >/dev/null

echo >&2
echo "== 2. Try the gestures opens live practice ==" >&2
open_screen row_demo practice_page
if ui_nodes | grep -q 'resource-id="demo_phone"'; then
  fail "no drawn phone on the live page"
else
  pass "no drawn phone on the live page"
fi
if practice_step_reaches 1; then pass "the live page shows step 1"; else fail "the live page shows step 1"; fi

echo >&2
echo "== 3. route 1 in practice, right after a dump: Back is caught, nothing runs ==" >&2
settle
reset_log
back_swipe
assert_log "the Back swipe is caught" "PRACTICE_CAUGHT LEFT Back"
assert_no_log "nothing ran for it" "ACTION_PERFORMED"
assert_no_log "the report layer caught it, not the backstop" "PRACTICE_BLOCKED"
result="$(ui_nodes | grep -m1 'resource-id="practice_result"' || true)"
if [ -n "${result}" ]; then pass "the live page still shows"; else fail "the live page still shows"; fi
case "${result}" in
  *"text=\"$(string_res gesture_back_name) detected\""*) pass "the result reads Back detected" ;;
  *) fail "the result reads Back detected (got: ${result})" ;;
esac
if practice_step_reaches 2; then pass "the pass moves to step 2"; else fail "the pass moves to step 2"; fi

echo >&2
echo "== 4. Home in practice: caught, and the app stays in front ==" >&2
settle
reset_log
home_swipe
assert_log "the Home swipe is caught" "PRACTICE_CAUGHT BOTTOM Home"
assert_no_log "nothing ran for it" "ACTION_PERFORMED"
assert_no_log "no backstop refusal" "PRACTICE_BLOCKED"
case "$(resumed_activity)" in
  *"${PKG}/"*) pass "the app is still in front" ;;
  *) fail "the app is still in front" ;;
esac
if practice_step_reaches 3; then pass "the pass moves to step 3"; else fail "the pass moves to step 3"; fi

echo >&2
echo "== 5. Recents in practice: caught ==" >&2
settle
reset_log
recents_swipe
assert_log "the held swipe is caught" "PRACTICE_CAUGHT BOTTOM Recents"
assert_no_log "nothing ran for it" "ACTION_PERFORMED"
assert_no_log "no backstop refusal" "PRACTICE_BLOCKED"
if practice_step_reaches 4; then pass "the pass moves to step 4"; else fail "the pass moves to step 4"; fi

echo >&2
echo "== 6. route 7: a swipe that arms too late is a miss and is not replayed ==" >&2
settle
reset_log
slow_swipe
assert_log "the slow swipe is a miss with its reason" "PRACTICE_MISSED TOO_SLOW_TO_ARM"
assert_no_log "it is not replayed" "ACTION_PERFORMED replay_swipe"
assert_no_log "nothing is dispatched" "REPLAY_DISPATCHED"
assert_no_log "no backstop refusal" "PRACTICE_BLOCKED"
close_practice
reset_log
slow_swipe
assert_log "control: with practice closed the same swipe is replayed" "ACTION_PERFORMED replay_swipe"

if [ -n "${DEBUG}" ]; then
  echo >&2
  echo "== 7. route 2 (debug): an Open app action that waits 80 ms runs nothing ==" >&2
  "${ADB_BIN}" shell settings put global navgesture_slot_left_whole_long app:com.android.settings >/dev/null
  rebind_with "a left long swipe that opens Settings"
  open_practice
  advance_to 5
  settle
  reset_log
  long_swipe
  assert_log "the long swipe is caught" "PRACTICE_CAUGHT LEFT app:com.android.settings"
  assert_no_log "no app was launched" "ACTION_PERFORMED launch"
  assert_no_log "no backstop refusal" "PRACTICE_BLOCKED"
  sleep 2
  case "$(resumed_activity)" in
    *com.android.settings*) fail "Settings did not open, not even after the delay" ;;
    *) pass "Settings did not open, not even after the delay" ;;
  esac
  close_practice
  reset_log
  long_swipe
  assert_log "control: with practice closed it opens Settings" "ACTION_PERFORMED launch com.android.settings"
  clear_overrides

  # Eight launchable apps as favourites, as corner.sh does. The emulator has no usage events, so
  # the recent apps list is empty and only the favourites drag can pick an app.
  FAVS="$("${ADB_BIN}" shell cmd package query-activities --brief -a android.intent.action.MAIN \
    -c android.intent.category.LAUNCHER 2>/dev/null | tr -d '\r' | sed -n 's#^ *\([a-z][a-z0-9_.]*\)/.*#\1#p' |
    grep -v -e "^${PKG}\$" -e '^com.android.vending$' | sort -u | head -8 | paste -sd, -)"
  FIRST="${FAVS%%,*}"

  echo >&2
  echo "== 8. routes 3 and 4 (debug): a drag to the favourites and a lift open nothing ==" >&2
  "${ADB_BIN}" shell settings put global navgesture_favourites "${FAVS}" >/dev/null
  "${ADB_BIN}" shell settings put global navgesture_app_list_style DRAG >/dev/null
  rebind_with "favourites and the Drag style"
  open_practice
  advance_to 4
  settle
  reset_log
  swipe_up_drag $((WIDTH / 2)) -320
  assert_log "the pick is caught" "PRACTICE_CAUGHT AppList "
  assert_no_log "no app was launched" "SWITCHER_LAUNCHED"
  assert_no_log "nothing ran for it" "ACTION_PERFORMED"
  assert_no_log "no backstop refusal" "PRACTICE_BLOCKED"
  close_practice
  reset_log
  swipe_up_drag $((WIDTH / 2)) -320
  assert_log "control: with practice closed the same drag launches the app" "SWITCHER_LAUNCHED"
  open_test_app

  echo >&2
  echo "== 9. route 5 (debug): a tap on the list left open opens nothing ==" >&2
  "${ADB_BIN}" shell settings put global navgesture_app_list_style TAP >/dev/null
  rebind_with "favourites and the Tap style"
  open_practice
  advance_to 4
  settle
  reset_log
  swipe_up_drag $((WIDTH / 2)) -320
  assert_log "the list stays open for a tap" "SWITCHER_TOUCH_MODE"
  # No dump here: one can unbind the service, which closes the list. The cell is computed as
  # corner.sh does: 72 x 84 dp cells, 12 dp padding, the panel 8 dp above the bottom strip (or
  # 40 dp above the screen bottom, if higher).
  STRIP_H=$((HEIGHT - BOTTOM_Y + 8))
  CELL="$(awk -v w="${WIDTH}" -v h="${HEIGHT}" -v dpi="${DENSITY}" -v strip="${STRIP_H}" 'BEGIN {
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
  assert_log "the tap on the first app is caught" "PRACTICE_CAUGHT AppList ${FIRST}"
  assert_no_log "no app was launched" "SWITCHER_LAUNCHED"
  assert_no_log "no backstop refusal" "PRACTICE_BLOCKED"
  close_practice
  clear_overrides

  echo >&2
  echo "== 10. corner (debug): a corner shortcut runs nothing ==" >&2
  "${ADB_BIN}" shell settings put global navgesture_left_corner notifications >/dev/null
  rebind_with "a left corner that opens notifications"
  open_practice
  advance_to 5
  settle
  reset_log
  corner_swipe 30 1
  assert_log "the corner swipe is caught" "PRACTICE_CAUGHT BOTTOM CornerLEFT"
  assert_no_log "the notifications did not open" "ACTION_PERFORMED notifications"
  assert_no_log "no backstop refusal" "PRACTICE_BLOCKED"
  close_practice
  reset_log
  corner_swipe 30 1
  assert_log "control: with practice closed it opens the notifications" "ACTION_PERFORMED notifications"
  "${ADB_BIN}" shell cmd statusbar collapse >/dev/null 2>&1 || true
  sleep 1
  clear_overrides
  rebind_with "no override"
fi

echo >&2
echo "== 11. the shade takes focus, and practice takes the gate again after it ==" >&2
open_practice
"${ADB_BIN}" shell cmd statusbar expand-settings >/dev/null 2>&1 || true
sleep 1
"${ADB_BIN}" shell cmd statusbar collapse >/dev/null 2>&1 || true
sleep 1
screen="$(ui_nodes)"
if printf '%s\n' "${screen}" | grep -q 'resource-id="practice_page"'; then
  pass "the live page shows after the shade"
else
  fail "the live page shows after the shade"
fi
if printf '%s\n' "${screen}" | grep -q 'resource-id="practice_paused"'; then
  fail "the paused notice is gone"
else
  pass "the paused notice is gone"
fi
settle
reset_log
back_swipe
assert_log "the Back swipe is caught again" "PRACTICE_CAUGHT LEFT Back"
assert_no_log "no backstop refusal" "PRACTICE_BLOCKED"
close_practice

echo >&2
echo "== 12. a Back that reaches the app closes practice and offers Try again ==" >&2
open_practice
"${ADB_BIN}" shell input keyevent KEYCODE_BACK >/dev/null
sleep 1
screen="$(ui_nodes)"
if printf '%s\n' "${screen}" | grep -q 'resource-id="practice_page"'; then
  fail "the Back closes practice"
else
  pass "the Back closes practice"
fi
# The text holds an apostrophe, which the string file escapes and the dump may too, so only the
# part after it is matched.
own_back="$(string_res practice_own_back | sed 's/.*own Back/own Back/')"
if printf '%s\n' "${screen}" | grep -q "${own_back}"; then
  pass "the screen under it says it was the phone's own Back"
else
  fail "the screen under it says it was the phone's own Back"
fi
tap_node "$(printf '%s\n' "${screen}" | grep -m1 "text=\"$(string_res practice_try_again)\"" || true)"
sleep 1
if practice_shown; then pass "Try again opens practice again"; else fail "Try again opens practice again"; fi
if practice_step_reaches 1; then pass "practice opens again at step 1"; else fail "practice opens again at step 1"; fi

echo >&2
echo "== 13. Close, then a real Back at once ==" >&2
close_bounds="$(bounds_of_id practice_close)"
settle
reset_log
back_swipe
assert_log "the gate is on: the Back swipe is caught" "PRACTICE_CAUGHT LEFT Back"
assert_no_log "no backstop refusal" "PRACTICE_BLOCKED"
if [ -z "${close_bounds}" ]; then
  fail "Close has bounds"
else
  # shellcheck disable=SC2086 # the four bounds are meant to split
  set -- ${close_bounds}
  cx=$((($1 + $3) / 2))
  cy=$((($2 + $4) / 2))
  reset_log
  # One shell, with no dump in between: the gate can only be off because Close ended it at once,
  # not through the lease or a rebind. The 200 ms slide is still running, so this also proves the
  # page lets go while it leaves.
  "${ADB_BIN}" shell "input tap ${cx} ${cy}; sleep 0.3; input swipe 2 ${MID_Y} 300 ${MID_Y} 120" >/dev/null
  sleep 2
  assert_log "the Back right after Close is real" "ACTION_PERFORMED Back"
  assert_no_log "and it was not caught" "PRACTICE_CAUGHT"
fi

if [ -n "${DEBUG}" ]; then
  echo >&2
  echo "== 14. the finish card (debug): every swipe is real again ==" >&2
  for name in navgesture_slot_right_whole_short navgesture_slot_bottom_short navgesture_slot_bottom_pause; do
    "${ADB_BIN}" shell settings put global "${name}" none >/dev/null
  done
  "${ADB_BIN}" shell settings put global navgesture_edge_app_list 0 >/dev/null
  rebind_with "a one-step plan"
  open_screen row_demo practice_page
  if step_text | grep -q ' 1 of 1$'; then pass "the plan has one step"; else fail "the plan has one step"; fi
  settle
  reset_log
  back_swipe
  assert_log "the one step's Back swipe is caught" "PRACTICE_CAUGHT LEFT Back"
  assert_no_log "no backstop refusal" "PRACTICE_BLOCKED"
  waited=0
  finished=''
  while [ "${waited}" -lt 5 ]; do
    if ui_nodes | grep -q 'resource-id="demo_done"'; then
      finished='1'
      break
    fi
    sleep 1
    waited=$((waited + 1))
  done
  if [ -n "${finished}" ]; then pass "the finish card shows"; else fail "the finish card shows"; fi
  settle
  reset_log
  back_swipe
  assert_log "on the finish card a Back swipe is real" "ACTION_PERFORMED Back"
fi

report "LIVE PRACTICE"
