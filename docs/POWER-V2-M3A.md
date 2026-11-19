# Power V2 M3a: essential thermal transaction and unlocked scheduling

Review-only slice stacked on PR17 under the approved Guardian engineering plan. No merge or release.

## Changed
- Critical thermal snapshot and activity-log row now commit in one Room DAO transaction. SQL failure in the second insert rolls back the first. No schema change or record deletion.
- The shared thermal warning attempt precedes persistence, so unavailable storage does not suppress the urgent warning path. Persistence error returns worker failure, never success/retry, with a bounded local failure note if that writer works. Cancellation is rethrown. A post attempt is not proof Android delivered a notification.
- Removed the receiver's Direct Boot opt-in and LOCKED_BOOT_COMPLETED filter. This avoids starting the credential-storage Application and its injected storage-backed graph before unlock merely to handle a locked-boot broadcast. Normal BOOT_COMPLETED remains the post-unlock opportunity; app entry also retains normal startup scheduling. No FGS/audio start, device-protected copy of private data, unlock polling or Force Stop bypass.
- Receiver and periodic scheduling entry both fail closed unless UserManager reports unlocked. Unknown unlock state defers scheduling.

## Evidence to inspect
Added second-write trigger-failure rollback, concurrent paired writes, real database close/reopen, manifest/unlock gating, and worker storage-failure warning tests. Full Android tests/lint/debug build await CI. These tests are not hardware power-loss/flash-durability or a physical reboot/Direct Boot test. Physical device/drain/Android17 runtime NOT RUN.

M3 is not complete: charge/discharge cross-session transactions, schema export/migration-safe corruption visibility and durable bounded recovery state remain later slices. Optional health-check execution remains budget-deferred. Q3/Q4 and charging implementation are separate. No UI redesign or new settings consent.

Reference: https://developer.android.com/privacy-and-security/direct-boot
