# Power V2.0 M0/M1: mode policy

## Scope and dependency

This module implements the first mode-policy slice of the approved engineering plan. It is stacked on correction PR #12, pinned to `b755c1bb30fc5df87d24cdaa38134c602c7fb1e2`. Its pull request targets `fix/targeted-stability-audit` so the review diff contains V2 changes only. Do not merge the dependency or this module automatically. If the dependency changes, reconcile the base and repeat exact-head checks.

Baseline observed on 19 November 2026: main `dccf94d5925c8a7d81b70da5e5e8db18a5cd4303`, docs-only update; correction PR #12 open, draft and unmerged. Baseline checks: correction run 44457865139 and main run 44466491816 succeeded. The correction's 113 tests are historical evidence, not M1 acceptance.

The owner approved the plan, a stack on the pinned correction head, and a 30-second thermal-clear dwell. Q3/Q4 health/Bluetooth decisions remain open and unchanged. No SDK, dependency, manifest, signing, settings, permission, networking, release or measurement change is included.

## Behavior changes

- Compute battery restriction with a pure reducer. Charging never cancels low-battery or OS Battery Saver restrictions.
- Battery thresholds: below 5% Survival, exactly 5% Critical, below 15% Critical, 15-30% Saver, otherwise Normal before independent restrictions. Recovery exits are 8%, 18% and 33%. Exactly 5% is the approved exception to Survival hysteresis in either direction. After that observation, subsequent valid levels use the resulting Critical state.
- Keep battery-only hysteresis separate from Saver, standby and thermal restrictions. Missing battery level retains the prior battery state, with an output floor of Critical until a valid level arrives. No invented battery percentage is stored.
- MODERATE or reported battery-health OVERHEAT imposes at least one stricter mode. SEVERE imposes at least Critical and at least one stricter mode. CRITICAL, EMERGENCY and SHUTDOWN impose Survival.
- Escalation is immediate. Lower-severity thermal reports cannot clear an active higher restriction. Restoration requires supported OS NONE with no reported OVERHEAT, observed over at least 30 seconds using a monotonic clock. Unknown, LIGHT or hot input interrupts recovery. A backwards elapsed clock restarts the dwell.
- The 30-second dwell is an owner-approved experiment parameter, not a measured safe-temperature threshold. This module adds no timer, polling, alarm or wake lock. Recovery is evaluated only when existing callers evaluate telemetry. No new execution opportunity is promised.
- Existing output structure and scheduling intervals are preserved. Shared authoritative-state ownership, feature gates, durable recovery and truthful measured diagnostics remain later modules. Thermal state remains process-local in M1; restart durability is not claimed.

## Files and risks

`BatteryModePolicy.kt` and `ThermalModePolicy.kt` contain pure policy; `AdaptivePowerTierEngine.kt` integrates them while preserving synchronized calls. New JVM tests cover boundary/property cases and regressions. Existing thermal tests now use explicit elapsed times instead of assuming immediate recovery.

Expected benefit: consistent restrictions and fewer premature restorations. Power savings are NOT VERIFIED. Risk: longer restriction after thermal events and more conservative work while battery data is unavailable. Minimum-version/OEM/background behavior still needs actual Android validation.

## Verification record

- Test-first integration on unmodified engine: three expected failures reproduced charging/Saver bypass, unknown-start Normal and early Survival exit.
- Local Kotlin 2.2.10/JVM 11 compilation against actual production model and policy source: 19 JUnit tests PASS. These include loops over 0-100%, all prior battery modes, charging and Saver combinations, explicit thermal severity, interrupted/elapsed-clock recovery and non-ratcheting engine integration.
- Offline privacy script: PASS. `git diff --check`: PASS.
- Full Android unit suite/lint/debug assembly: pending exact-head CI. No local Android SDK/build result is claimed.
- Physical device, drain, Android 17 runtime, crash/restart persistence, feature-gate enforcement and release-variant testing: NOT RUN in this module.
- Numeric budgets, measured recovery SLOs and before/after savings: OPEN, NOT VERIFIED.

## Rollback and gates

The module changes no schema or preferences. Revert its policy/integration/test commits together if necessary; no data deletion or schema downgrade is needed. Do not restore a known unsafe charging/Saver bypass as an unreviewed workaround. Stop on test or quota failure. No CI rerun or paid workaround is authorized. Merge and release each require the owner's explicit per-PR OK after exact-head evidence. M1 is not final V2 acceptance.
