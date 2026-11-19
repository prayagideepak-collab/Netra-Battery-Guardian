# Power V2 M2b: first consumer enforcement slice

## Dependency and approval

Stacked on M2a PR #14, pinned `43a8a8185947a6653f817693cb39b8ffb324430f`. M1 PR #13 and correction PR #12 remain dependencies, unmerged. Owner approved the visible deferral preview and all-mode deferral of unbudgeted optional work on 19 November 2026. No temporary numeric caps or automatic merge/release are authorized. Q3/Q4 health/Bluetooth scope remains unchanged.

## Enforced paths

- UI export, queued export and cache-cleanup actions return the approved status before starting a coroutine, queuing new work, creating an export file, opening the chosen output stream or deleting cache. Cancelled destination selection still does nothing.
- Direct CSV/PDF exporter entrypoints also reject the missing budget, preventing callers from bypassing the UI check. Structured charging export returns a skipped result. Plain file-returning methods throw a clear deferral error before any output directory is created.
- Existing screen-off analysis, storage audit/cleanup, retention, optional health-check diagnostic, daily cycle observation, charging export and routine idle-persistence workers finish their current invocation without optional work. A missing budget is not a retry signal. Previously scheduled periodic work remains scheduled; no new wake source is added. Scheduling removal/minimization is later background-work work.
- Service/UI usage analysis and cycle observation recheck the central decision. The underlying health estimator and Bluetooth behavior are not rewritten while Q3/Q4 remain open.
- Ordinary milestone dispatch from the rules engine and threshold service defers. Existing consented urgent discharge levels 1-5%, charger transitions, low-battery notifications, thermal warnings and controlled restoration remain eligible. Eligibility never overrides consent, media/call rules, platform restrictions or permissions.
- Routine periodic/hourly snapshots and repeated session-history updates defer. Initial session state, charger transitions and explicitly forced state snapshots remain preserved. Atomicity, minimal essential deltas, crash/restart and batching remain M3 work.
- OS thermal warning thresholds no longer rise merely because the battery mode is Survival. A SEVERE OS report at 3% can still issue its advisory. Cooldown/escalation deduplication remains; no temperature-only safety claim is introduced.

## Deliberate remaining work

This is a first enforcement slice, not completion of every M2 consumer or V2 acceptance. Full subscription-driven UI state, lifecycle cancellation, acquisition-time input provenance, cross-path alert ownership, static/cached chart rendering and rendering minimization remain. Existing low-cost visible telemetry and read-only filesystem-capacity display are preserved; directory scans are gated. Existing helper implementations are retained for later measured-budget integration and regression tests, not exposed as a bypass through current application actions.

No measured budget exists, so the central gate remains fail-closed. Do not relax the gate by inserting an arbitrary number. No real-device, drain, Android 17 audio/runtime or measured power result is claimed. Power impact and numeric budgets remain NOT VERIFIED.

## Verification record

- Local actual-source Kotlin/JVM policy tests: 32 PASS (29 earlier plus 3 missing-budget/essential/guard tests).
- Existing worker/export/threshold tests updated to assert newly approved deferral, no export directory, unchanged stored records and preserved urgent alert dispatch rather than expecting the old optional work.
- New direct CSV/PDF bypass regression, Survival OS-SEVERE alert regression and phone-width snackbar visual fixture added.
- Privacy script and whitespace checks PASS.
- Exact-head Android unit/lint/debug assembly and visual artifact inspection: pending CI.
- Actual full activity tap-to-destination/cache cancellation, physical permissions/background behavior and device measurements: NOT RUN.

Rollback changes no schema or settings. Revert this slice together, retaining M1/M2a, with explicit review before re-enabling unbudgeted work. Stop on a failed check and investigate; no quota workaround or speculative rerun. Merge/release requires explicit per-PR owner approval.
