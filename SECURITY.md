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


## Guardian 1.0.3 / versionCode 4

Adds approved PR19 charging observations and PR20 health/accessory completeness
to the prior PR12-18 release. This is not an Android OS security patch. Energy
counter rate estimates are not charger-output watts; independent electrical
readings are not a fallback speed classifier. Unknown OS health is unavailable,
and no health percent is inferred from remaining charge divided by level.
Optional accessory reads and ordinary charging-type speech remain deferred
without a measured budget. No new permission, discovery, service, sampler,
Bluetooth device speech, signing identity, secret or release-guard change.

Exact-head CI, established certificate continuity and independent APK package,
version and hash readback remain publication gates. Physical counter/accessory
support, measurement accuracy, install/upgrade/reboot, OEM callbacks, speech
routing and drain tests are NOT RUN. No device-stability, accuracy, cooling or
power-saving guarantee. Keep Play Protect ON. Prior1.0.2 section is historical.
