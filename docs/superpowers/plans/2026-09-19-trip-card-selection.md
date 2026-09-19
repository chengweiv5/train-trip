# Trip Card Selection Implementation Plan

> **For agentic workers:** Execute inline in this session; superpowers execution skills are not installed. The user has approved implementation. Steps use checkbox syntax for tracking.

**Status:** 实现、设计、14 项相关用例、真机安装已验证；提交推送在最终交付阶段回读。

**Goal:** Make train cards selectable as a whole, display seats as information, and allow launching 12306 without selection.

**Architecture:** Keep the temporary train key in AppViewModel. Put one selectable action on each card, keep its seat-details button independent, and render optional summary above an unconditional App-launch button.

**Tech Stack:** Kotlin, Jetpack Compose, Android instrumentation, Pencil MCP.

## Global Constraints

- Version remains 0.4.0 / code 8. Preserve current compact layout and horizontal date scrolling.
- No purchase, automatic clipboard writes, browser fallback or automatic prefill.
- Keep seat filters and ticket quantity matching semantics unchanged.
- Design spec: `docs/superpowers/specs/2026-09-19-trip-card-selection-design.md`; reviewed Approved.

### Task 1: Train-level selection and interaction

**Files:** `app/src/main/kotlin/cn/traintrip/app/AppViewModel.kt`, `ui/DetailScreen.kt`, `ui/DetailActions.kt`; tests `DetailFlowTest.kt`, `DetailLayoutTest.kt`, `AvailableSeatFlowTest.kt` under `app/src/androidTest/kotlin/cn/traintrip/app/`.

**Interfaces:** `select(trip: Trip)`, `clearSelection()`, `DetailScreen(..., onSelect: (Trip) -> Unit, ...)`, `DetailActions(selected: Trip?, people: Int, notice: String?, onOpenApp: () -> Unit)`, `SeatAvailabilityLabel(text: String)`.

- [x] Remove `selectedSeat` from state and copy calls. Selection becomes `mutable.update { it.copy(selectedTripKey=trip.key, notice=null) }`.
- [x] Refresh validity becomes `affected && old.selectedTripKey != null && (chosen == null || !chosen.confirmed(old.applied ?: old.filters))`; clear with “所选车次已不符合当前条件，请重新选择”. Preserve failure/cancellation behavior.
- [x] Make failure text conditional on `selectedTripKey != null`, retaining the existing selected-copy text only when applicable.
- [x] Use `Modifier.selectable(selected=t.key==s.selectedTripKey, role=Role.RadioButton, onClick={onSelect(t)})` within the clipped card, plus a selectable group for the list. Seat labels have no click/selection semantics; the existing TextButton opens details.
- [x] Render summary only when `selected != null`: heading `"${selected.trainCode} · ${selected.date.monthValue}月${selected.date.dayOfMonth}日"`, route includes both times and `+N 天`, then adult count. Remove launch button enablement dependency.
- [x] Update existing selection helpers and assertions to card semantics. Use unmerged text lookup for timing/seat checks. Replace seat touch-target checks with >=48dp card/main/more checks.
- [x] Add physical touch tests for train number, time, station, seat label and card padding; check selected card semantics and only one selection. Test More opens without selecting or switching cards. Test unselected launch/failed launch and refresh from two available seats to only FIRST available.
- [x] Build APK/test APK and run `:app:lintDebug --offline`; run `DetailFlowTest,DetailLayoutTest,AvailableSeatFlowTest` on the task's dedicated emulator. Inspect JUnit completion and runtime captures.

### Task 2: Design synchronization and delivery

**Files:** `design/train-trip-v0.4.0.pen`, `design/v0.4.0-recovery.json`, `design/reference/v0.4.0/`, `design/validation/v0.4.0-*.json`, current README/spec, new verification record.

**Interfaces:** editable `.pen` accessed only via Pencil MCP; recovery JSON stores native variables and root nodes.

- [x] Convert seat component/instances to neutral data labels and remove selected overrides. Mark each card action `select-trip`, preserving independent `all-seat-status` actions.
- [x] Update selection instructions, train-only summary, unselected footer with active launch button and no selection prerequisite. Update refresh/failure/accessibility rules.
- [x] Save with Pencil, copy saved source and independently open through MCP; compare whole native hierarchy. Export current JPEG/PDF, run bounds/reference/overlap checks and OCR, inspect final selected/unselected/compact/feedback renders.
- [x] Update current docs and mark historical seat-selection rules superseded. Record test and design evidence in `docs/verification/2026-09-19-trip-card-selection.md`.
- [x] Preserve old APK and settings, install with `adb -s FMR0224725012307 install -r`, pull installed package and compare SHA256; compare settings before/after and verify foreground activity.
- [ ] Commit on feature branch; fetch/rebase current main if needed, verify, then `git push origin HEAD:main` and fetch/read back. Close only the task emulator, update task state, send punk-12 completion notification.

## Self-review

All approved requirements map to the two tasks. Native launcher remains unchanged; all references to `selectedSeat` and button-like seat behavior are removed from production. Existing query logic, version and unrelated layouts remain unchanged.
