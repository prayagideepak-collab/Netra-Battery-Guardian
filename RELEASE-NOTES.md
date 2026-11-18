# Netra Battery Guardian 1.0.0

First standalone Guardian release, signed with the established Netra ecosystem
certificate. Android 7.0 (API 24) or newer. Package:
`com.aistudio.netrabattery.kxmzqr`; versionCode 1.

- Local battery and temperature monitoring, history and private user-selected
  report export. Unsupported measurements show Unavailable.
- Native home-screen widget fed by the existing monitor, without a second
  polling service. Readings are labeled as the last observed sample. English and
  Hindi labels, signed sensor readings, full state and observed-session estimates
  are shown without inventing missing values.
- Optional cooling brightness consent starts OFF, including upgrades. Android
  Modify system settings permission is separate. Saved-setting recovery keeps
  newer manual choices and reports unresolved/ambiguous recovery honestly.
- Media-aware voice gate and observed-session approximate time estimates.

## Verification and limits

Automated unit, privacy, lint and build checks are required on the exact main
candidate. Android test renders are controlled fixtures, not phone readings.
Physical-device launcher resizing, on-device voice routing and real document
picker acceptance are not yet verified. No zero-battery-impact or guaranteed
cooling claim is made. The app cannot install Android OS security patches.

This signing certificate may differ from an earlier AI Studio development build.
Android may require uninstalling that build before installing this release, which
can remove its local data. This release does not claim cross-app history import.
