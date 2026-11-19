# Power V2 M2a: published policy state and typed decisions

This is the first slice of M2, not completion of feature enforcement.

## Dependency

Stacked on M1 PR #13 at `5178e04dab86caed4429a1d1f54cc59f8b202d52`, which itself depends on correction PR #12 at `b755c1bb30fc5df87d24cdaa38134c602c7fb1e2`. The M2a PR targets M1's branch so its diff is separate. Do not merge any dependency or this PR automatically. Reconcile any changed base and repeat exact-head checks before acceptance.

## Changes

- Reuse the existing injected singleton AdaptivePowerTierEngine as the single process-local state owner rather than adding another engine or competing manager.
- Publish a read-only StateFlow of immutable PowerPolicyState: accepted-evaluation sequence, monotonic timestamp, active mode, battery/thermal availability, foreground/screen state, restriction reasons and matching metrics.
- Initial state is explicitly not observed, without fictional telemetry or metrics.
- Reject evaluations with an elapsed timestamp older than the published evaluation before changing battery or thermal reducer state. Existing callers still stamp at evaluation time; this does not yet solve stale collection or delayed broadcasts. Source-aware acquisition stamps and lifecycle ownership remain consumer-integration work.
- Add typed feature importance, mode decisions, reason and next existing opportunity. Keep essential warnings, consented urgent alerts, state integrity and controlled restoration distinct from routine, optional and decorative work.
- A decision API rechecks sequence before a later batch. A changed sequence invalidates optional work's earlier snapshot, even if the new mode happens to match. Essential ALLOW means only that this power gate does not suppress the work. Consent, permission, OS execution eligibility and component recovery checks still apply.
- Mode eligibility alone never implies a finite approved power budget. All optional decisions from the central API remain DEFER/BUDGET_NOT_VERIFIED until measurement-backed limits exist. No budget was fabricated and no test-time budget override is exposed on the central API.

## Deliberate limits

Existing service, worker and UI execution paths are not migrated to the new decision API in this PR. Their old behavior remains until M2b integration and the required visible-behavior preview. This PR cannot be described as enforcing the full feature matrix. It adds no new collector, scheduler, network monitor, permission, timer, polling, log, wake lock, storage schema, device measurement or UI. Q3/Q4 are unchanged.

Sequence validation protects against an intervening published evaluation, not an approved maximum age. No age budget has been measured. The new state and thermal latch are process-local, not durable; M3 must address restart/recovery semantics. Existing per-caller diagnostic counts remain unchanged for later truthful-diagnostics work.

## Verification

- Local Kotlin 2.2.10/JVM 11, actual production models and kotlinx-coroutines 1.10.2: 29 JUnit tests PASS, including all 19 M1 tests and 10 new state/decision tests.
- New cases: unobserved state, matching published metrics/reasons, out-of-order rejection, immutable prior snapshots, missing-budget deferral, essential-opportunity handling, changed sequence during work, 100 concurrent publications, importance/mode matrix, Doze/unknown/visibility reasons.
- Privacy script and whitespace checks PASS.
- Exact-head Android unit suite, lint and debug assembly: pending CI. Real device/drain, Android 17 runtime, full consumer enforcement, persistence and measured budgets: NOT RUN or NOT VERIFIED.

Rollback has no schema/data impact: revert this slice while preserving M1. Expected benefit is a consistent decision source and testable checks, not measured savings. Merge/release needs explicit owner per-PR approval. Stop on failed checks; do not rerun around a quota failure.
