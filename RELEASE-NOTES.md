# Netra Battery Guardian 1.0.2

Incremental test release containing approved PRs #12 through #18. Charging #19
and health/Bluetooth #20 are NOT included. Package
`com.aistudio.netrabattery.kxmzqr`, versionCode 3, minimum Android API 24.

## Changes

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
full feature-matrix acceptance and measured savings remain unfinished. Charging
speech is not newly delivered; charging #19 and health/Bluetooth #20 stay excluded.
No guaranteed cooling, zero battery impact, stable-phone certification or Android
OS security-patch claim is made. Keep Play Protect ON.

Uses the established Netra release signing certificate. A prior AI Studio build
may use another certificate; Android can require uninstalling that build, which
can remove local data. No cross-app history import is claimed.
