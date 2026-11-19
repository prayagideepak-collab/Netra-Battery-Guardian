# Charging speed from observed stored-energy change

Separate draft PR on the approved PR17 stack. No merge or release is requested.

## What this rate means

Android `BATTERY_PROPERTY_ENERGY_COUNTER` reports remaining battery energy in nWh. Two valid observations over monotonic collection-start time produce an estimated net stored-energy rate. This is not charger output or a direct charging-input measurement. Voltage times current remains separate signed battery-side telemetry and never supplies the speed category. Existing observed-level ETA is unchanged.

Source: https://developer.android.com/reference/android/os/BatteryManager

## Event-only policy

- Reuses existing battery observations. No new polling job, timer, wake lock, foreground service or permission.
- A bounded 64-point reducer covers a real observed 10-second rolling window, interpolating only its oldest partial interval.
- A category must then remain stable across 10 seconds of actual observations. A changed category is unavailable during its new stability period, rather than retaining the old band.
- Events more than 10 seconds apart, insufficient window coverage, zero/invalid/missing/unchanged/decreasing counter values, stopped/full charging and restart leave speed unavailable. An identical duplicate of the same timestamp/value is idempotent. Backward elapsed timestamps start a new observation window.
- Dense event bursts can exhaust the bounded window before it covers 10 seconds. That honestly stays unavailable.

| Estimated rate | Category |
|---|---|
| positive, below 5 W | Slow |
| 5 to below 10 W | Normal |
| 10 to below 20 W | Fast |
| 20 to below 50 W | Ultra Fast |
| 50 to below 100 W | Hyper Fast |
| 100 W or more | Extreme Fast |

The existing persisted enum names remain compatible with history: STANDARD is displayed as Normal, SUPER_FAST remains a legacy historical value but is not emitted by this classifier, and HYPER_FAST is appended.

## Speech and preference

An independent persisted charging-type toggle defaults ON. It does not change charging milestone preferences. Type phrases contain only the type and charging, with no watts or sentence about a guessed charger.

Guardian's existing Power V2 fail-closed measured-budget rule still applies. Ordinary type speech remains deferred in every mode until a measured budget is verified. The setting states that limitation. This PR does not invent a power cap or enable a background service.

The existing single TTS owner handles type speech. Superseded or unavailable types clear pending type speech, no type fallback notification is posted, and lazy-init dispatch rejects old observations. Type speech is not queued behind other speech and cannot replace an unrelated pending utterance. Essential start/stop/full ownership stays unchanged; the connect utterance no longer embeds a guessed speed category.

## Verification

- Local actual-source JVM tests: 12 passed, covering exact boundaries, stability, sparse samples, invalid counters, ordering, bounded memory, restart and independence from V x I.
- Added Android/Robolectric tests for persisted OFF, type phrases, budget deferral, stale dispatch, pending cancellation and connect-event independence.
- Added real Compose screenshot tests for the estimated-rate dashboard and independent toggle. Existing missing-reading, signed-current/power and widget tests remain.
- Privacy scan and whitespace check pass locally.
- Full Android tests, lint, APK assembly and actual screenshot inspection: pending CI at PR creation, not claimed complete here.
- Physical-phone counter support, accuracy, background delivery, TTS routing and measured battery/thermal cost: NOT RUN.
