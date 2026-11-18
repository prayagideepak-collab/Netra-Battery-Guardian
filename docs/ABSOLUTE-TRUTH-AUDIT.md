# Guardian device-data audit, 18 November 2026

Scope: direct update e3f8da2 after v1.0.0, followed by the owner's request to hide unsupported readings and use phone API observations only.

## Corrected in this PR

- No fixed Doze savings percent/mAh or efficiency score. Only observed transition duration/count remains.
- No arbitrary optimizer health score or healthy-100 default in UI. Advice is not a measured battery-health verdict.
- Storage chart uses filesystem totals and measured Netra cache only. The rest is unattributed occupied space, never inferred media, APK, downloads or Android OS categories.
- Signed current is retained. Charging status does not prove an OEM's sensor polarity or measure charger input power.
- Thermal classifications, notifications and optimizer now use OS overheat/thermal signals, not fabricated device-specific Celsius limits. Charts and live view remove inferred thermal threshold bands. New widget shows temperature without inferred safety labels.
- Missing shared numeric readings are blank, and unsupported new dashboard facts are hidden. Real zero readings remain zero.
- Counter-derived capacity requires a real positive charge counter and valid observed percentage. No cached/default 4500 mAh is used. This is an estimate, not rated/design capacity.
- Room migration column default matches its entity. Existing session updates refresh percentage gained.
- Pending CSV work is cancelled and app-private reports are cleared by reset, serialized against charging CSV creation.
- Existing night-silent and screen-off speech policy is retained.

## Audit evidence and limits

- Current static offline/backup/cleartext privacy guard passed.
- 288 reachable historical blobs scanned for known credential patterns and key-file extensions: no matches. All existing v1.0.0 release assets and decompressed APK contents checked similarly: no matches. This is bounded detection, not proof that every possible secret is absent.
- GitHub secret scanning returned zero open alerts. Dependency and CodeQL alerts were not accessible.
- Direct update's original CI passed. This correction's CI and visual inspection are pending.
- Physical-device battery properties, database upgrade, widget refresh, voice routing and document-picker acceptance are NOT RUN.
- Conservation scan gates remain app policies, not OEM limits. Old report records are marked unverified; legacy design/wear/health is not re-exported as observations. This PR does not certify universal device support or measured battery savings.

No secret values or signing key files belong in this report. No release is authorized by this document.
