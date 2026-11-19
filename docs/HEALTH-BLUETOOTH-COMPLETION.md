# Guardian health observations and Bluetooth completion

Approved Q16 plan, separate draft on PR18. Charging implementation remains on its separate branch. No merge or release requested.

## Health

The existing Android Battery Observations card now shows Android's health category and independently available temperature, voltage, OS cycle count and remaining-charge counter. Unknown is unavailable, not Good. Health percentage is explicitly Unavailable because this collector has no verified full-charge and design-capacity pair. Dividing remaining charge by battery level does not establish measured battery health. No score, percentage bar, green lifetime claim or new health engine is added. Existing stored health/cycle records remain intact.

## Bluetooth

- Reuses ConnectedBluetoothMonitor and its existing foreground card. Permission choices, supported-device-only APIs, no discovery, no location access and no speech are preserved.
- Missing or invalid accessory battery values now say Unavailable rather than blank or zero. Invalid RSSI is not displayed as a measured dBm sample.
- Both the card and monitor enforce the central measured-budget gate for optional accessory reads. This holds in all modes and cannot be bypassed by passing the UI flag true. A visible English/Hindi reason explains why the accessory is not read.
- Connection snapshots remain explicitly last refresh, not always-live status. RSSI labels remain stronger/intermediate/weaker signal estimates, not distance, stability or disconnect predictions.
- Existing pause/dispose cleanup remains. The finite expiration callback is bound to its read generation so an older expiration cannot clear a newer read. No new timer, polling loop, service, permission or automatic reconnect is added.

## Verification limits

Added Android/Robolectric assertions and actual Compose screenshots for unknown health, independent Hindi readings, invalid/missing Bluetooth data, visible deferral and monitor-level enforcement. Existing denied-permission, signal-label and cleanup tests remain. Full Android CI, lint, debug assembly and actual pixel inspection are pending at PR creation. Physical accessory support, callback behavior, background behavior and battery/thermal cost remain NOT RUN.
