# Security and release scope

## Guardian 1.0.2 / versionCode 3

This incremental application release contains PRs #12-#18 only. PRs #19 and #20
are excluded. It is not an Android OS security patch and does not change the
phone's vendor patch level.

The existing privacy gate, exact-main automated build gate, established signing
certificate check, immutable version tag and draft-only staging remain required.
Signed artifact package/version/certificate/hash readback must pass before
publication. No signing key, repository permission or secret is changed.

Power-mode eligibility is not permission or consent. Optional work remains
fail-closed without a measured budget. OS thermal observations are not universal
numeric OEM safety limits; a warning attempt does not prove delivery. Critical
snapshot/log writes are transactional, but this does not prove hardware power-loss
durability. Credential-storage work is scheduled only after unlock.

Physical-device install/upgrade, reboot, Android 17 behavior, speech routing,
widgets, document picker and drain tests are NOT RUN. No zero-impact, measured
saving, cooling, device-stability or universal background-delivery guarantee.
See RELEASE-NOTES.md and docs/POWER-V2-* for the scoped automated evidence and
unfinished work. Keep Play Protect ON.
