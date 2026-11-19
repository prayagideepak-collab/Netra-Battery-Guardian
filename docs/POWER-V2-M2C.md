# Power V2 M2c: collection order and UI subscription

Stacked on M2b PR #15 at `560dc9e8f129f72d92fac7314df1b65564ef3768`. Dependencies remain unmerged. No automatic merge or release is permitted.

## Changes

The collector stamps the beginning of each collection with a monotonic clock. The shared engine uses that stamp rather than evaluation time when available, so an older collection that finishes later cannot overwrite a newer accepted policy. The published state labels whether collection provenance exists. Existing synthetic/legacy callers without it retain the evaluation-clock fallback, explicitly marked as lacking collection provenance.

This stamp is NOT the generation time of the sticky battery broadcast, the age of an OS property or a universal freshness guarantee. Millisecond-equal samples remain serialized in evaluation order. No maximum-age budget is invented. A delayed critical observation may be rejected as older than a newer observation; cross-input timestamps and urgent-event ownership still need further integration/testing. The collection stamp is process-local, not persisted across reboot.

The visible UI subscribes to the existing singleton policy StateFlow. A restriction published by a service or thermal consumer can update its power status without a UI polling cycle. The subscription stops when the UI is not visible and resumes with current state. The refresh completion reads the latest shared policy rather than always writing its earlier local policy copy. Snapshot-count display remains its own historical count, not a fabricated shared power measurement.

Rules-engine duplicate normalization excludes collection provenance, just as it already excludes wall-clock time and foreground state. Equivalent observations do not become different work merely because their collection timestamp differs.

No layout, new UI wording, permission, setting, SDK, dependency, schema, timer, polling, alarm, wake lock, health algorithm or Bluetooth behavior change. No optional-budget relaxation. Q3/Q4 remain unchanged.

## Evidence and limits

Local actual-source Kotlin/JVM policy suite: 36 tests PASS, including four new collection-order/provenance/subscription tests. Added Android collector timestamp test and expanded equivalent-event dedup regression. Privacy script and diff checks PASS. Exact-head Android suite/lint/debug assembly: pending CI.

The subscription test is a StateFlow test, not an end-to-end activity/service test. Real lifecycle/background/device interactions, drain and Android 17 runtime are NOT RUN. Full cancellation, cross-path alert ownership, rendering changes and remaining M2 integration are not complete. M3 durability/recovery and M4/M5 background/measurement work remain.

Expected benefit is ordering consistency and less stale presentation, not measured power savings. Numeric budgets remain OPEN. Rollback changes no persisted data: revert this slice with no schema downgrade. Stop on check or quota failure; no speculative rerun or paid workaround. Merge/release requires explicit per-PR owner OK.
