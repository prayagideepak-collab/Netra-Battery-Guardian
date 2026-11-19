# Netra Battery Guardian 1.0.3

Combined test release containing approved PRs #12 through #20. Package
`com.aistudio.netrabattery.kxmzqr`, versionCode 4, minimum Android API 24.

## Changes since 1.0.2

- Charging type uses observed Android ENERGY_COUNTER changes over elapsed time,
  not voltage times current or claimed charger-output watts. Missing, unsupported,
  stale, invalid or insufficient evidence remains Unavailable. Existing signed
  battery-side electrical telemetry and observed-level ETA stay separate.
- Charging-type phrases are short type-only text with a separate default-ON
  setting that preserves saved OFF. IMPORTANT: ordinary charging-type speech
  remains budget-deferred in every mode. This release does not enable it without
  a verified measured budget. Existing essential start/stop/full speech ownership
  remains unchanged; no Bluetooth device speech is added.
- Android Battery Observations shows the OS health category and independently
  available temperature, voltage, cycle count and remaining-charge counter.
  Unknown is Unavailable, not Good. Health percentage stays Unavailable without
  verified full-charge/design-capacity evidence, no invented health score.
- Foreground accessory reads enforce the central missing-budget deferral in
  both UI and monitor. Missing accessory values are Unavailable; RSSI is a signal
  estimate, not distance or connection-stability prediction. Last-refresh labels
  remain, and older read expiration cannot clear a newer read. No scanning,
  permission, background sampler or Bluetooth speech is added.

## Retained from 1.0.2

- Shared process-local power policy, conservative battery-mode hysteresis and
  OS-thermal restriction/recovery decisions. Charging does not cancel low-battery
  or Battery Saver restrictions. The 30-second observed recovery dwell is an
  approved experiment parameter, not a measured safe-temperature threshold.
- Optional exports, cache cleanup, routine analyses and animations defer while
  measured budgets are unavailable. No numeric budget or power saving is invented.
  Essential warning eligibility still respects permissions, consent and platform rules.
- Shared policy state and collection-order stamps reduce stale updates. Visible UI
  subscribes to current state; the timed foreground refresh loop is removed.
  History charts cache their first populated input until screen re-entry. Entry,
  OS callback and manual telemetry refresh remain.
- Shared process-local thermal notification episode ownership deduplicates equal
  or lower severity, escalates higher severity, and rejects stale clear observations.
  A notification attempt is not proof Android delivered it.
- Essential critical-thermal snapshot and activity log commit in one Room
  transaction. Warning attempts precede persistence; storage failures are not
  reported as successful saves. Background scheduling requires unlocked storage;
  pre-unlock Direct Boot startup is removed.
- Targeted stability corrections serialize/coalesce battery-event work, cache
  usage-stat reads, bound cache traversal, preserve urgent/milestone paths and
  distinguish unavailable OS thermal observations. Connected Bluetooth remains
  readings-only using public APIs, with no device announcements or scanning.

## Verification and limits

Exact-head automated test, lint and debug-build evidence and signed-candidate
identity/hash readback are publication gates. Test renders use controlled fixtures,
not this phone's readings. Physical-phone install/upgrade, reboot/Direct Boot,
launcher/widget resizing, background speech routing, document-picker acceptance,
Android 17 runtime, power-loss durability and drain measurements are NOT RUN.

This is not completion of Power V2 or M3 recovery. Episode state remains
process-local. Cross-session transaction/recovery work, finite measured budgets,
full feature-matrix acceptance and measured savings remain unfinished. Ordinary charging-type
speech remains budget-deferred despite the setting; no measured budget is added.
No guaranteed cooling, zero battery impact, stable-phone certification or Android
OS security-patch claim is made. Keep Play Protect ON.

Uses the established Netra release signing certificate. A prior AI Studio build
may use another certificate; Android can require uninstalling that build, which
can remove local data. No cross-app history import is claimed.
